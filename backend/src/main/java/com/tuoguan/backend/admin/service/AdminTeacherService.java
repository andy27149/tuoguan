package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.DuplicatePhoneException;
import com.tuoguan.backend.admin.web.InvalidTransferTargetException;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.dao.TaskTemplateDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminTeacherService {

    public enum DeleteMode { DELETE_ALL, TRANSFER }

    public record TeacherDeletionImpact(int classCount, int studentCount, int templateCount, boolean hasStudents) {
    }

    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;
    private final ClassRoomDao classRoomDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final ClassDismissalDao classDismissalDao;
    private final TaskTemplateDao taskTemplateDao;

    public AdminTeacherService(TeacherDao teacherDao, PasswordEncoder passwordEncoder, ClassRoomDao classRoomDao,
                                StudentDao studentDao, DailyTaskDao dailyTaskDao,
                                StudentDailyNoteDao studentDailyNoteDao,
                                StudentArrivalCheckinDao studentArrivalCheckinDao,
                                ClassDismissalDao classDismissalDao, TaskTemplateDao taskTemplateDao) {
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
        this.classRoomDao = classRoomDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.classDismissalDao = classDismissalDao;
        this.taskTemplateDao = taskTemplateDao;
    }

    public Teacher createTeacher(Long institutionId, String phone, String name, String initialPassword) {
        if (teacherDao.findByPhone(phone).isPresent()) {
            throw new DuplicatePhoneException("Phone already registered: " + phone);
        }
        Long id = teacherDao.insert(new Teacher(null, institutionId, phone, name,
                passwordEncoder.encode(initialPassword), Role.TEACHER, true, null));
        return teacherDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Teacher not found after insert: " + id));
    }

    public List<Teacher> listTeachers(Long institutionId) {
        return teacherDao.findAllByInstitutionId(institutionId);
    }

    public TeacherDeletionImpact getDeletionImpact(Long institutionId, Long teacherId) {
        requireTeacherInInstitution(institutionId, teacherId);
        List<ClassRoom> classRooms = classRoomDao.findAllByTeacherId(teacherId);
        int studentCount = classRooms.stream().mapToInt(cr -> studentDao.findAllByClassRoomId(cr.id()).size()).sum();
        int templateCount = taskTemplateDao.findAllByInstitutionIdAndTeacherId(institutionId, teacherId).size();
        return new TeacherDeletionImpact(classRooms.size(), studentCount, templateCount, studentCount > 0);
    }

    @Transactional
    public void deleteTeacher(Long institutionId, Long teacherId, DeleteMode mode, Long targetTeacherId) {
        requireTeacherInInstitution(institutionId, teacherId);
        List<ClassRoom> classRooms = classRoomDao.findAllByTeacherId(teacherId);
        boolean hasStudents = classRooms.stream().anyMatch(cr -> !studentDao.findAllByClassRoomId(cr.id()).isEmpty());

        if (hasStudents && mode == DeleteMode.TRANSFER) {
            Teacher target = requireTeacherInInstitution(institutionId, targetTeacherId);
            if (target.id().equals(teacherId)) {
                throw new InvalidTransferTargetException("不能转移给自己");
            }
            classRoomDao.reassignTeacher(teacherId, targetTeacherId);
            taskTemplateDao.reassignTeacher(teacherId, targetTeacherId);
        } else {
            for (ClassRoom classRoom : classRooms) {
                dailyTaskDao.deleteAllByClassRoomId(classRoom.id());
                studentDailyNoteDao.deleteAllByClassRoomId(classRoom.id());
                studentArrivalCheckinDao.deleteAllByClassRoomId(classRoom.id());
                classDismissalDao.deleteAllByClassRoomId(classRoom.id());
                studentDao.deleteAllByClassRoomId(classRoom.id());
                classRoomDao.deleteById(classRoom.id());
            }
            taskTemplateDao.deleteAllByTeacherId(teacherId);
        }
        teacherDao.deleteById(teacherId);
    }

    private Teacher requireTeacherInInstitution(Long institutionId, Long teacherId) {
        Teacher teacher = teacherDao.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("Teacher not found: " + teacherId));
        if (!teacher.institutionId().equals(institutionId)) {
            throw new NotFoundException("Teacher not found: " + teacherId);
        }
        return teacher;
    }
}
