package com.tuoguan.backend.course.service;

import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseMealRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseMealRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.course.web.CourseNotEnrolledException;
import com.tuoguan.backend.course.web.CoursePriceNotConfiguredException;
import com.tuoguan.backend.course.web.CourseRosterEntry;
import com.tuoguan.backend.course.web.DuplicateConsumptionException;
import com.tuoguan.backend.course.web.OffCampusMealNotApplicableException;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CourseConsumptionService {

    private static final Logger log = LoggerFactory.getLogger(CourseConsumptionService.class);

    private final CourseService courseService;
    private final StudentDao studentDao;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final CourseConsumptionRecordDao consumptionRecordDao;
    private final CourseRechargeRecordDao rechargeRecordDao;
    private final CourseMealRecordDao mealRecordDao;

    public CourseConsumptionService(CourseService courseService, StudentDao studentDao,
                                     StudentUnitEnrollmentDao enrollmentDao,
                                     CourseConsumptionRecordDao consumptionRecordDao,
                                     CourseRechargeRecordDao rechargeRecordDao,
                                     CourseMealRecordDao mealRecordDao) {
        this.courseService = courseService;
        this.studentDao = studentDao;
        this.enrollmentDao = enrollmentDao;
        this.consumptionRecordDao = consumptionRecordDao;
        this.rechargeRecordDao = rechargeRecordDao;
        this.mealRecordDao = mealRecordDao;
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
    // 语义；下面补的日志只记录"本日已点名，共到 N 人"，不改变这个语义。
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

        List<CourseConsumptionRecord> recorded = presentStudentIds.stream()
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
        log.info("课外课点名消课：institutionId={}, courseId={}, date={}, teacherId={}, 提交到={}, 实际新记={}",
                teachingUnit.institutionId(), courseId, date, teacherId, presentStudentIds.size(), recorded.size());
        return recorded;
    }

    // 纯课外课学生此前完全没有用餐记录的入口——只对报了这门课、且没有托管班（双重身份
    // 学生的用餐走托管看板那套，这里拒绝记录，避免同一天被算两次餐费）的学生生效。
    // 交互上和托管看板的"用餐"图标一致：老师点一下立即生效，不是先勾选再批量确认。
    // 按学生+日期去重（而不是按"这门课"去重）：同一个孩子今天不管报了几门课、被哪个老师
    // 先标记，都只算一次——这是业务事实本身的语义（"今天吃没吃饭"不分课），不是实现细节。
    // 用原子的 upsert（跟 kanban 模块 StudentMealRecordDao.upsert 同款写法）而不是
    // "先查是否存在、不存在才插入"，天然对并发安全：同一个学生当天报了两门课，两个
    // 老师几乎同时点击记用餐，不会有竞态窗口，也不需要额外 catch 唯一约束冲突。
    public void setMeal(Long teacherId, Long courseId, Long studentId, LocalDate date) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        requireEligibleOffCampusStudent(teachingUnit, studentId);
        mealRecordDao.upsert(new CourseMealRecord(null, teachingUnit.institutionId(), studentId, date,
                teacherId, null));
    }

    public void clearMeal(Long teacherId, Long courseId, Long studentId, LocalDate date) {
        TeachingUnit teachingUnit = courseService.getOwnedByTeacher(teacherId, courseId);
        requireEligibleOffCampusStudent(teachingUnit, studentId);
        mealRecordDao.delete(studentId, date);
    }

    private void requireEligibleOffCampusStudent(TeachingUnit teachingUnit, Long studentId) {
        enrollmentDao.findByStudentIdAndTeachingUnitId(studentId, teachingUnit.id())
                .filter(StudentUnitEnrollment::active)
                .orElseThrow(() -> new CourseNotEnrolledException("Student not enrolled in course: " + studentId));
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (student.teachingUnitId() != null) {
            throw new OffCampusMealNotApplicableException(
                    "该学生已有托管班，用餐请在托管看板登记: " + studentId);
        }
    }
}
