package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.DuplicatePhoneException;
import com.tuoguan.backend.admin.web.InvalidTeacherRoleException;
import com.tuoguan.backend.admin.web.InvalidTransferTargetException;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.dao.TaskTemplateDao;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminTeacherService {

    public enum DeleteMode { DELETE_ALL, TRANSFER }

    public record TeacherDeletionImpact(int classCount, int studentCount, int templateCount, int courseCount,
                                         boolean hasStudents) {
    }

    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;
    private final TeachingUnitDao teachingUnitDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final ClassDismissalDao classDismissalDao;
    private final TaskTemplateDao taskTemplateDao;
    private final ClassBillingRateDao classBillingRateDao;
    private final StudentUnitEnrollmentDao studentUnitEnrollmentDao;
    private final CourseConsumptionRecordDao courseConsumptionRecordDao;
    private final CourseRechargeRecordDao courseRechargeRecordDao;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final MonthlyBillDao monthlyBillDao;

    public AdminTeacherService(TeacherDao teacherDao, PasswordEncoder passwordEncoder,
                                TeachingUnitDao teachingUnitDao, StudentDao studentDao, DailyTaskDao dailyTaskDao,
                                StudentDailyNoteDao studentDailyNoteDao,
                                StudentArrivalCheckinDao studentArrivalCheckinDao,
                                ClassDismissalDao classDismissalDao, TaskTemplateDao taskTemplateDao,
                                ClassBillingRateDao classBillingRateDao,
                                StudentUnitEnrollmentDao studentUnitEnrollmentDao,
                                CourseConsumptionRecordDao courseConsumptionRecordDao,
                                CourseRechargeRecordDao courseRechargeRecordDao,
                                StudentLeaveRecordDao studentLeaveRecordDao, MonthlyBillDao monthlyBillDao) {
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
        this.teachingUnitDao = teachingUnitDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.classDismissalDao = classDismissalDao;
        this.taskTemplateDao = taskTemplateDao;
        this.classBillingRateDao = classBillingRateDao;
        this.studentUnitEnrollmentDao = studentUnitEnrollmentDao;
        this.courseConsumptionRecordDao = courseConsumptionRecordDao;
        this.courseRechargeRecordDao = courseRechargeRecordDao;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.monthlyBillDao = monthlyBillDao;
    }

    public Teacher createTeacher(Long institutionId, String phone, String name, String initialPassword, Role role) {
        if (role == Role.PLATFORM_ADMIN) {
            throw new InvalidTeacherRoleException("Cannot create a teacher with role: " + role);
        }
        if (teacherDao.findByPhone(phone).isPresent()) {
            throw new DuplicatePhoneException("Phone already registered: " + phone);
        }
        Long id = teacherDao.insert(new Teacher(null, institutionId, phone, name,
                passwordEncoder.encode(initialPassword), role != null ? role : Role.TEACHER, true, null));
        return teacherDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Teacher not found after insert: " + id));
    }

    public List<Teacher> listTeachers(Long institutionId) {
        return teacherDao.findAllByInstitutionId(institutionId);
    }

    public Teacher renameTeacher(Long institutionId, Long teacherId, String name) {
        requireTeacherInInstitution(institutionId, teacherId);
        teacherDao.updateName(teacherId, name);
        return teacherDao.findById(teacherId)
                .orElseThrow(() -> new IllegalStateException("Teacher not found after update: " + teacherId));
    }

    public TeacherDeletionImpact getDeletionImpact(Long institutionId, Long teacherId) {
        requireTeacherInInstitution(institutionId, teacherId);
        List<TeachingUnit> units = teachingUnitDao.findAllByTeacherId(teacherId);
        int classCount = (int) units.stream().filter(u -> u.billingMode() == BillingMode.MONTHLY).count();
        int courseCount = (int) units.stream().filter(u -> u.billingMode() == BillingMode.LESSON_COUNT).count();
        int studentCount = units.stream()
                .filter(u -> u.billingMode() == BillingMode.MONTHLY)
                .mapToInt(u -> studentDao.findAllByTeachingUnitId(u.id()).size())
                .sum();
        int templateCount = taskTemplateDao.findAllByInstitutionIdAndTeacherId(institutionId, teacherId).size();
        return new TeacherDeletionImpact(classCount, studentCount, templateCount, courseCount, studentCount > 0);
    }

    @Transactional
    public void deleteTeacher(Long institutionId, Long teacherId, DeleteMode mode, Long targetTeacherId) {
        requireTeacherInInstitution(institutionId, teacherId);
        List<TeachingUnit> units = teachingUnitDao.findAllByTeacherId(teacherId);
        boolean hasStudents = units.stream()
                .filter(u -> u.billingMode() == BillingMode.MONTHLY)
                .anyMatch(u -> !studentDao.findAllByTeachingUnitId(u.id()).isEmpty());

        if (hasStudents && mode == DeleteMode.TRANSFER) {
            Teacher target = requireTeacherInInstitution(institutionId, targetTeacherId);
            if (target.id().equals(teacherId)) {
                throw new InvalidTransferTargetException("不能转移给自己");
            }
            teachingUnitDao.reassignAllTeacher(teacherId, targetTeacherId);
            taskTemplateDao.reassignTeacher(teacherId, targetTeacherId);
        } else {
            for (TeachingUnit unit : units) {
                if (unit.billingMode() == BillingMode.MONTHLY) {
                    dailyTaskDao.deleteAllByTeachingUnitId(unit.id());
                    studentDailyNoteDao.deleteAllByTeachingUnitId(unit.id());
                    studentArrivalCheckinDao.deleteAllByTeachingUnitId(unit.id());
                    classDismissalDao.deleteAllByTeachingUnitId(unit.id());
                    classBillingRateDao.deleteAllByTeachingUnitId(unit.id());
                    monthlyBillDao.deleteAllByTeachingUnitId(unit.id());
                    studentLeaveRecordDao.deleteAllByTeachingUnitId(unit.id());
                    for (var student : studentDao.findAllByTeachingUnitId(unit.id())) {
                        studentUnitEnrollmentDao.deleteAllByStudentId(student.id());
                        courseConsumptionRecordDao.deleteAllByStudentId(student.id());
                        courseRechargeRecordDao.deleteAllByStudentId(student.id());
                    }
                    studentDao.deleteAllByTeachingUnitId(unit.id());
                    teachingUnitDao.deleteById(unit.id());
                } else {
                    for (var enrollment : studentUnitEnrollmentDao.findAllByTeachingUnitId(unit.id())) {
                        studentDao.findById(enrollment.studentId())
                                .filter(s -> s.teachingUnitId() == null)
                                .ifPresent(s -> {
                                    courseConsumptionRecordDao.deleteAllByStudentId(s.id());
                                    courseRechargeRecordDao.deleteAllByStudentId(s.id());
                                    studentUnitEnrollmentDao.deleteAllByStudentId(s.id());
                                    studentDao.deleteAllByStudentId(s.id());
                                });
                    }
                    studentUnitEnrollmentDao.deleteAllByTeachingUnitId(unit.id());
                    courseConsumptionRecordDao.deleteAllByTeachingUnitId(unit.id());
                    teachingUnitDao.deleteById(unit.id());
                }
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
