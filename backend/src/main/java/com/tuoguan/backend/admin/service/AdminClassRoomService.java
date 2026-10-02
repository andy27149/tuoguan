package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.AdminClassRoomResponse;
import com.tuoguan.backend.auth.dao.TeacherDao;
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
import com.tuoguan.backend.roster.web.DuplicateClassNameException;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminClassRoomService {

    public record ClassRoomDeletionImpact(int studentCount) {
    }

    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;
    private final StudentDao studentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final ClassDismissalDao classDismissalDao;
    private final ClassBillingRateDao classBillingRateDao;
    private final StudentUnitEnrollmentDao studentUnitEnrollmentDao;
    private final CourseConsumptionRecordDao courseConsumptionRecordDao;
    private final CourseRechargeRecordDao courseRechargeRecordDao;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final MonthlyBillDao monthlyBillDao;

    public AdminClassRoomService(TeachingUnitDao teachingUnitDao, TeacherDao teacherDao, StudentDao studentDao,
                                  DailyTaskDao dailyTaskDao, StudentDailyNoteDao studentDailyNoteDao,
                                  StudentArrivalCheckinDao studentArrivalCheckinDao,
                                  ClassDismissalDao classDismissalDao, ClassBillingRateDao classBillingRateDao,
                                  StudentUnitEnrollmentDao studentUnitEnrollmentDao,
                                  CourseConsumptionRecordDao courseConsumptionRecordDao,
                                  CourseRechargeRecordDao courseRechargeRecordDao,
                                  StudentLeaveRecordDao studentLeaveRecordDao,
                                  MonthlyBillDao monthlyBillDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.teacherDao = teacherDao;
        this.studentDao = studentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.classDismissalDao = classDismissalDao;
        this.classBillingRateDao = classBillingRateDao;
        this.studentUnitEnrollmentDao = studentUnitEnrollmentDao;
        this.courseConsumptionRecordDao = courseConsumptionRecordDao;
        this.courseRechargeRecordDao = courseRechargeRecordDao;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.monthlyBillDao = monthlyBillDao;
    }

    public List<AdminClassRoomResponse> listClassRooms(Long institutionId) {
        Map<Long, Teacher> teacherById = teacherDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Teacher::id, t -> t));
        return teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(c -> c.billingMode() == BillingMode.MONTHLY)
                .map(c -> {
                    Teacher teacher = teacherById.get(c.teacherId());
                    return new AdminClassRoomResponse(c.id(), c.name(), c.teacherId(),
                            teacher != null ? teacher.name() : "-", teacher != null ? teacher.phone() : "-");
                })
                .toList();
    }

    public AdminClassRoomResponse updateClassRoom(Long institutionId, Long classRoomId, String name, Long teacherId) {
        requireClassRoomInInstitution(institutionId, classRoomId);
        Teacher teacher = requireTeacherInInstitution(institutionId, teacherId);
        boolean duplicate = teachingUnitDao.findAllByTeacherId(teacherId).stream()
                .filter(c -> c.billingMode() == BillingMode.MONTHLY)
                .anyMatch(c -> c.name().equals(name) && !c.id().equals(classRoomId));
        if (duplicate) {
            throw new DuplicateClassNameException("Class name already exists: " + name);
        }
        teachingUnitDao.updateNameAndTeacher(classRoomId, name, teacherId);
        return new AdminClassRoomResponse(classRoomId, name, teacher.id(), teacher.name(), teacher.phone());
    }

    public ClassRoomDeletionImpact getDeletionImpact(Long institutionId, Long classRoomId) {
        TeachingUnit teachingUnit = requireClassRoomInInstitution(institutionId, classRoomId);
        return new ClassRoomDeletionImpact(studentDao.findAllByTeachingUnitId(teachingUnit.id()).size());
    }

    @Transactional
    public void deleteClassRoom(Long institutionId, Long classRoomId) {
        TeachingUnit teachingUnit = requireClassRoomInInstitution(institutionId, classRoomId);
        dailyTaskDao.deleteAllByTeachingUnitId(teachingUnit.id());
        studentDailyNoteDao.deleteAllByTeachingUnitId(teachingUnit.id());
        studentArrivalCheckinDao.deleteAllByTeachingUnitId(teachingUnit.id());
        classDismissalDao.deleteAllByTeachingUnitId(teachingUnit.id());
        classBillingRateDao.deleteAllByTeachingUnitId(teachingUnit.id());
        monthlyBillDao.deleteAllByTeachingUnitId(teachingUnit.id());
        studentLeaveRecordDao.deleteAllByTeachingUnitId(teachingUnit.id());
        for (var student : studentDao.findAllByTeachingUnitId(teachingUnit.id())) {
            studentUnitEnrollmentDao.deleteAllByStudentId(student.id());
            courseConsumptionRecordDao.deleteAllByStudentId(student.id());
            courseRechargeRecordDao.deleteAllByStudentId(student.id());
        }
        studentDao.deleteAllByTeachingUnitId(teachingUnit.id());
        teachingUnitDao.deleteById(teachingUnit.id());
    }

    private TeachingUnit requireClassRoomInInstitution(Long institutionId, Long classRoomId) {
        TeachingUnit teachingUnit = teachingUnitDao.findById(classRoomId)
                .filter(c -> c.billingMode() == BillingMode.MONTHLY)
                .orElseThrow(() -> new NotFoundException("ClassRoom not found: " + classRoomId));
        if (!teachingUnit.institutionId().equals(institutionId)) {
            throw new NotFoundException("ClassRoom not found: " + classRoomId);
        }
        return teachingUnit;
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
