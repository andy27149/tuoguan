package com.tuoguan.backend.unit.service;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.web.DuplicateCourseNameException;
import com.tuoguan.backend.kanban.dao.ClassDismissalDao;
import com.tuoguan.backend.kanban.dao.DailyTaskDao;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.dao.StudentDailyNoteDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.DuplicateClassNameException;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.unit.web.AdminTeachingUnitResponse;
import com.tuoguan.backend.unit.web.CreateTeachingUnitRequest;
import com.tuoguan.backend.unit.web.FeatureDisabledException;
import com.tuoguan.backend.unit.web.InvalidTeachingUnitRequestException;
import com.tuoguan.backend.unit.web.UpdateTeachingUnitRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminTeachingUnitService {

    public record TeachingUnitDeletionImpact(int studentCount) {
    }

    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;
    private final InstitutionDao institutionDao;
    private final StudentDao studentDao;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final DailyTaskDao dailyTaskDao;
    private final StudentDailyNoteDao studentDailyNoteDao;
    private final StudentArrivalCheckinDao studentArrivalCheckinDao;
    private final ClassDismissalDao classDismissalDao;
    private final ClassBillingRateDao classBillingRateDao;
    private final CourseConsumptionRecordDao courseConsumptionRecordDao;
    private final CourseRechargeRecordDao courseRechargeRecordDao;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final MonthlyBillDao monthlyBillDao;

    public AdminTeachingUnitService(TeachingUnitDao teachingUnitDao, TeacherDao teacherDao,
                                     InstitutionDao institutionDao, StudentDao studentDao,
                                     StudentUnitEnrollmentDao enrollmentDao, DailyTaskDao dailyTaskDao,
                                     StudentDailyNoteDao studentDailyNoteDao,
                                     StudentArrivalCheckinDao studentArrivalCheckinDao,
                                     ClassDismissalDao classDismissalDao, ClassBillingRateDao classBillingRateDao,
                                     CourseConsumptionRecordDao courseConsumptionRecordDao,
                                     CourseRechargeRecordDao courseRechargeRecordDao,
                                     StudentLeaveRecordDao studentLeaveRecordDao, MonthlyBillDao monthlyBillDao) {
        this.teachingUnitDao = teachingUnitDao;
        this.teacherDao = teacherDao;
        this.institutionDao = institutionDao;
        this.studentDao = studentDao;
        this.enrollmentDao = enrollmentDao;
        this.dailyTaskDao = dailyTaskDao;
        this.studentDailyNoteDao = studentDailyNoteDao;
        this.studentArrivalCheckinDao = studentArrivalCheckinDao;
        this.classDismissalDao = classDismissalDao;
        this.classBillingRateDao = classBillingRateDao;
        this.courseConsumptionRecordDao = courseConsumptionRecordDao;
        this.courseRechargeRecordDao = courseRechargeRecordDao;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.monthlyBillDao = monthlyBillDao;
    }

    public List<AdminTeachingUnitResponse> list(Long institutionId, BillingMode billingModeFilter) {
        return teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(u -> billingModeFilter == null || u.billingMode() == billingModeFilter)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminTeachingUnitResponse create(Long institutionId, CreateTeachingUnitRequest request) {
        Institution institution = institutionDao.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Institution not found: " + institutionId));
        if (request.billingMode() == BillingMode.MONTHLY && !institution.custodyEnabled()) {
            throw new FeatureDisabledException("托管班功能未开启");
        }
        if (request.billingMode() == BillingMode.LESSON_COUNT && !institution.offCampusEnabled()) {
            throw new FeatureDisabledException("课外课程功能未开启");
        }
        if (request.billingMode() == BillingMode.LESSON_COUNT && request.lessonDurationMinutes() == null) {
            throw new InvalidTeachingUnitRequestException("课外课程必须填写课时时长");
        }
        Teacher teacher = requireTeacherInInstitution(institutionId, request.teacherId());
        requireNoDuplicateName(institutionId, request.billingMode(), request.name(), null);

        Integer lessonDurationMinutes = request.billingMode() == BillingMode.LESSON_COUNT
                ? request.lessonDurationMinutes() : null;
        java.math.BigDecimal pricePerLesson = request.billingMode() == BillingMode.LESSON_COUNT
                ? request.pricePerLesson() : null;
        Long id = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacher.id(), request.name(),
                request.billingMode(), lessonDurationMinutes, pricePerLesson, true, null));
        TeachingUnit unit = teachingUnitDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Teaching unit not found after insert: " + id));
        return toResponse(unit);
    }

    @Transactional
    public AdminTeachingUnitResponse update(Long institutionId, Long id, UpdateTeachingUnitRequest request) {
        TeachingUnit unit = requireUnitInInstitution(institutionId, id);

        if (request.name() != null && request.teacherId() != null) {
            requireTeacherInInstitution(institutionId, request.teacherId());
            requireNoDuplicateName(institutionId, unit.billingMode(), request.name(), id);
            teachingUnitDao.updateNameAndTeacher(id, request.name(), request.teacherId());
        } else if (request.teacherId() != null) {
            requireTeacherInInstitution(institutionId, request.teacherId());
            teachingUnitDao.reassignTeacher(id, request.teacherId());
        } else if (request.name() != null) {
            requireNoDuplicateName(institutionId, unit.billingMode(), request.name(), id);
            teachingUnitDao.updateNameAndTeacher(id, request.name(), unit.teacherId());
        }

        if (request.pricePerLesson() != null) {
            teachingUnitDao.setPrice(id, request.pricePerLesson());
        }
        if (request.active() != null) {
            teachingUnitDao.setActive(id, request.active());
        }

        TeachingUnit updated = teachingUnitDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Teaching unit not found after update: " + id));
        return toResponse(updated);
    }

    public TeachingUnitDeletionImpact getDeletionImpact(Long institutionId, Long id) {
        TeachingUnit unit = requireUnitInInstitution(institutionId, id);
        int studentCount;
        if (unit.billingMode() == BillingMode.MONTHLY) {
            studentCount = studentDao.findAllByTeachingUnitId(unit.id()).size();
        } else {
            studentCount = (int) enrollmentDao.findAllByTeachingUnitId(unit.id()).stream()
                    .filter(StudentUnitEnrollment::active)
                    .count();
        }
        return new TeachingUnitDeletionImpact(studentCount);
    }

    @Transactional
    public void delete(Long institutionId, Long id) {
        TeachingUnit unit = requireUnitInInstitution(institutionId, id);
        if (unit.billingMode() == BillingMode.MONTHLY) {
            dailyTaskDao.deleteAllByTeachingUnitId(unit.id());
            studentDailyNoteDao.deleteAllByTeachingUnitId(unit.id());
            studentArrivalCheckinDao.deleteAllByTeachingUnitId(unit.id());
            classDismissalDao.deleteAllByTeachingUnitId(unit.id());
            classBillingRateDao.deleteAllByTeachingUnitId(unit.id());
            monthlyBillDao.deleteAllByTeachingUnitId(unit.id());
            studentLeaveRecordDao.deleteAllByTeachingUnitId(unit.id());
            for (Student student : studentDao.findAllByTeachingUnitId(unit.id())) {
                enrollmentDao.deleteAllByStudentId(student.id());
                courseConsumptionRecordDao.deleteAllByStudentId(student.id());
                courseRechargeRecordDao.deleteAllByStudentId(student.id());
            }
            studentDao.deleteAllByTeachingUnitId(unit.id());
            teachingUnitDao.deleteById(unit.id());
        } else {
            for (StudentUnitEnrollment enrollment : enrollmentDao.findAllByTeachingUnitId(unit.id())) {
                studentDao.findById(enrollment.studentId())
                        .filter(s -> s.teachingUnitId() == null)
                        .ifPresent(s -> {
                            courseConsumptionRecordDao.deleteAllByStudentId(s.id());
                            courseRechargeRecordDao.deleteAllByStudentId(s.id());
                            enrollmentDao.deleteAllByStudentId(s.id());
                            studentDao.deleteAllByStudentId(s.id());
                        });
            }
            enrollmentDao.deleteAllByTeachingUnitId(unit.id());
            courseConsumptionRecordDao.deleteAllByTeachingUnitId(unit.id());
            teachingUnitDao.deleteById(unit.id());
        }
    }

    private void requireNoDuplicateName(Long institutionId, BillingMode billingMode, String name, Long excludeId) {
        boolean duplicate = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(u -> u.billingMode() == billingMode)
                .filter(u -> excludeId == null || !u.id().equals(excludeId))
                .anyMatch(u -> u.name().equals(name));
        if (duplicate) {
            if (billingMode == BillingMode.MONTHLY) {
                throw new DuplicateClassNameException("Class name already exists: " + name);
            } else {
                throw new DuplicateCourseNameException("Course name already exists: " + name);
            }
        }
    }

    private Teacher requireTeacherInInstitution(Long institutionId, Long teacherId) {
        Teacher teacher = teacherDao.findById(teacherId)
                .orElseThrow(() -> new NotFoundException("Teacher not found: " + teacherId));
        if (!teacher.institutionId().equals(institutionId)) {
            throw new NotFoundException("Teacher not found: " + teacherId);
        }
        return teacher;
    }

    private TeachingUnit requireUnitInInstitution(Long institutionId, Long id) {
        TeachingUnit unit = teachingUnitDao.findById(id)
                .orElseThrow(() -> new NotFoundException("Teaching unit not found: " + id));
        if (!unit.institutionId().equals(institutionId)) {
            throw new NotFoundException("Teaching unit not found: " + id);
        }
        return unit;
    }

    private AdminTeachingUnitResponse toResponse(TeachingUnit unit) {
        Teacher teacher = teacherDao.findById(unit.teacherId()).orElse(null);
        String teacherName = teacher != null ? teacher.name() : null;
        String teacherPhone = teacher != null ? teacher.phone() : null;
        return AdminTeachingUnitResponse.from(unit, teacherName, teacherPhone);
    }
}
