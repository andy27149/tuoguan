package com.tuoguan.backend.course.service;

import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.course.web.CourseNotEnrolledException;
import com.tuoguan.backend.course.web.CoursePriceNotConfiguredException;
import com.tuoguan.backend.course.web.CourseRosterEntry;
import com.tuoguan.backend.course.web.DuplicateConsumptionException;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CourseConsumptionService {

    private final CourseService courseService;
    private final StudentDao studentDao;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final CourseConsumptionRecordDao consumptionRecordDao;
    private final CourseRechargeRecordDao rechargeRecordDao;

    public CourseConsumptionService(CourseService courseService, StudentDao studentDao,
                                     StudentUnitEnrollmentDao enrollmentDao,
                                     CourseConsumptionRecordDao consumptionRecordDao,
                                     CourseRechargeRecordDao rechargeRecordDao) {
        this.courseService = courseService;
        this.studentDao = studentDao;
        this.enrollmentDao = enrollmentDao;
        this.consumptionRecordDao = consumptionRecordDao;
        this.rechargeRecordDao = rechargeRecordDao;
    }

    public List<CourseRosterEntry> listRosterForCourse(Long teacherId, Long courseId) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        return enrollmentDao.findAllByTeachingUnitId(teachingUnit.id()).stream()
                .filter(StudentUnitEnrollment::active)
                .map(enrollment -> {
                    Student student = studentDao.findById(enrollment.studentId())
                            .orElseThrow(() -> new IllegalStateException("Student not found: "
                                    + enrollment.studentId()));
                    Integer balance = resolveBalance(student, teachingUnit.id());
                    return CourseRosterEntry.from(student, balance);
                })
                .toList();
    }

    // 纯课外课学生恒显示余额（哪怕从没充值过，也是 0）；托管班学生（双重身份）现在也允许
    // 预充值，但消课主要还是走月度账单，没充值过就没有余额概念，只有充值过才展示余额。
    private Integer resolveBalance(Student student, Long teachingUnitId) {
        List<CourseRechargeRecord> recharges = rechargeRecordDao.findAllByStudentId(student.id()).stream()
                .filter(r -> r.teachingUnitId().equals(teachingUnitId))
                .toList();
        if (student.teachingUnitId() != null && recharges.isEmpty()) {
            return null;
        }
        int recharged = recharges.stream().mapToInt(CourseRechargeRecord::lessonCount).sum();
        int consumed = (int) consumptionRecordDao.findAllByStudentId(student.id()).stream()
                .filter(c -> c.teachingUnitId().equals(teachingUnitId))
                .count();
        return recharged - consumed;
    }

    public CourseConsumptionRecord recordConsumption(Long teacherId, Long courseId, Long studentId, LocalDate date,
                                                       boolean confirm) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        if (teachingUnit.pricePerLesson() == null) {
            throw new CoursePriceNotConfiguredException("Course price not configured: " + courseId);
        }
        enrollmentDao.findByStudentIdAndTeachingUnitId(studentId, teachingUnit.id())
                .filter(StudentUnitEnrollment::active)
                .orElseThrow(() -> new CourseNotEnrolledException("Student not enrolled in course: " + studentId));

        List<CourseConsumptionRecord> existing = consumptionRecordDao
                .findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date);
        if (!existing.isEmpty() && !confirm) {
            throw new DuplicateConsumptionException("Consumption already recorded for this date: " + date);
        }

        Long id = consumptionRecordDao.insert(new CourseConsumptionRecord(null, teachingUnit.institutionId(),
                studentId, teachingUnit.id(), date, teachingUnit.pricePerLesson(), teacherId, null));
        return consumptionRecordDao.findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date)
                .stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Consumption record not found: " + id));
    }

    // 设计权衡（产品诊断 #08）：未勾选的学生不生成任何记录，呼应「课外课无请假概念」的
    // 简化设计——事后无法区分「教师忘记点名」和「学生确实没来」。暂不建议改动这里的核心
    // 语义；全库目前还没有应用日志基础设施，若投诉增多，比起单独为这一处引入日志框架，
    // 更建议先统一规划一套应用日志方案（这里加一条「本日已点名，共到 N 人」的操作记录）。
    public List<CourseConsumptionRecord> recordBatchConsumption(Long teacherId, Long courseId, LocalDate date,
                                                                  List<Long> presentStudentIds) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        if (teachingUnit.pricePerLesson() == null) {
            throw new CoursePriceNotConfiguredException("Course price not configured: " + courseId);
        }

        List<Long> enrolledStudentIds = enrollmentDao.findAllByTeachingUnitId(teachingUnit.id()).stream()
                .filter(StudentUnitEnrollment::active)
                .map(StudentUnitEnrollment::studentId)
                .toList();

        return presentStudentIds.stream()
                .filter(enrolledStudentIds::contains)
                .filter(studentId -> consumptionRecordDao
                        .findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date).isEmpty())
                .map(studentId -> {
                    Long id = consumptionRecordDao.insert(new CourseConsumptionRecord(null,
                            teachingUnit.institutionId(), studentId, teachingUnit.id(), date,
                            teachingUnit.pricePerLesson(), teacherId, null));
                    return consumptionRecordDao
                            .findAllByStudentIdAndTeachingUnitIdAndDate(studentId, teachingUnit.id(), date)
                            .stream()
                            .filter(r -> r.id().equals(id))
                            .findFirst()
                            .orElseThrow(() -> new NotFoundException("Consumption record not found: " + id));
                })
                .toList();
    }
}
