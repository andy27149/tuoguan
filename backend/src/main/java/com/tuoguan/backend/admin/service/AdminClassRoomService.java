package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminClassRoomResponse;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.web.DuplicateClassNameException;
import com.tuoguan.backend.roster.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminClassRoomService {

    public record ClassRoomDeletionImpact(int studentCount) {
    }

    private final ClassRoomDao classRoomDao;
    private final TeacherDao teacherDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final ClassDismissalDao classDismissalDao;

    public AdminClassRoomService(ClassRoomDao classRoomDao, TeacherDao teacherDao, StudentDao studentDao,
                                  DailyTaskDao dailyTaskDao, StudentDailyNoteDao studentDailyNoteDao,
                                  StudentArrivalCheckinDao studentArrivalCheckinDao,
                                  ClassDismissalDao classDismissalDao) {
        this.classRoomDao = classRoomDao;
        this.teacherDao = teacherDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.classDismissalDao = classDismissalDao;
    }

    public List<AdminClassRoomResponse> listClassRooms(Long institutionId) {
        Map<Long, String> phoneByTeacherId = teacherDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Teacher::id, Teacher::phone));
        return classRoomDao.findAllByInstitutionId(institutionId).stream()
                .map(c -> new AdminClassRoomResponse(c.id(), c.name(), c.teacherId(),
                        phoneByTeacherId.getOrDefault(c.teacherId(), "-")))
                .toList();
    }

    public AdminClassRoomResponse updateClassRoom(Long institutionId, Long classRoomId, String name, Long teacherId) {
        requireClassRoomInInstitution(institutionId, classRoomId);
        Teacher teacher = requireTeacherInInstitution(institutionId, teacherId);
        boolean duplicate = classRoomDao.findAllByTeacherId(teacherId).stream()
                .anyMatch(c -> c.name().equals(name) && !c.id().equals(classRoomId));
        if (duplicate) {
            throw new DuplicateClassNameException("Class name already exists: " + name);
        }
        classRoomDao.update(classRoomId, name, teacherId);
        return new AdminClassRoomResponse(classRoomId, name, teacher.id(), teacher.phone());
    }

    public ClassRoomDeletionImpact getDeletionImpact(Long institutionId, Long classRoomId) {
        ClassRoom classRoom = requireClassRoomInInstitution(institutionId, classRoomId);
        return new ClassRoomDeletionImpact(studentDao.findAllByClassRoomId(classRoom.id()).size());
    }

    @Transactional
    public void deleteClassRoom(Long institutionId, Long classRoomId) {
        ClassRoom classRoom = requireClassRoomInInstitution(institutionId, classRoomId);
        dailyTaskDao.deleteAllByClassRoomId(classRoom.id());
        studentDailyNoteDao.deleteAllByClassRoomId(classRoom.id());
        studentArrivalCheckinDao.deleteAllByClassRoomId(classRoom.id());
        classDismissalDao.deleteAllByClassRoomId(classRoom.id());
        studentDao.deleteAllByClassRoomId(classRoom.id());
        classRoomDao.deleteById(classRoom.id());
    }

    private ClassRoom requireClassRoomInInstitution(Long institutionId, Long classRoomId) {
        ClassRoom classRoom = classRoomDao.findById(classRoomId)
                .orElseThrow(() -> new NotFoundException("ClassRoom not found: " + classRoomId));
        if (!classRoom.institutionId().equals(institutionId)) {
            throw new NotFoundException("ClassRoom not found: " + classRoomId);
        }
        return classRoom;
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
