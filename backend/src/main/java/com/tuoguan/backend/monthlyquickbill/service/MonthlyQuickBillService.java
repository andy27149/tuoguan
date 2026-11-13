package com.tuoguan.backend.monthlyquickbill.service;

import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.service.CourseConsumptionService;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;
import com.tuoguan.backend.kanban.service.StudentArrivalCheckinService;
import com.tuoguan.backend.kanban.service.StudentLeaveRecordService;
import com.tuoguan.backend.kanban.service.StudentMealRecordService;
import com.tuoguan.backend.monthlyquickbill.web.CourseConsumptionInput;
import com.tuoguan.backend.monthlyquickbill.web.InvalidQuickBillRequestException;
import com.tuoguan.backend.monthlyquickbill.web.QuickBillCourseResponse;
import com.tuoguan.backend.monthlyquickbill.web.QuickBillStudentResponse;
import com.tuoguan.backend.monthlyquickbill.web.QuickBillSubmitRequest;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// 把"这个学生这个月用餐几天/请假几天/课外课上了几节"这几个聚合数字，转换成最少必要的
// 真实到校/用餐/请假/消课记录，再交给现有账单引擎（BillGenerationService）计算——不新增
// 任何计费规则，只是省掉逐日打卡这一步。合成日期不追求"看起来像真的"，只保证数量和
// 不与已有记录冲突；默认到校时间固定写死，不是要以假乱真。
@Service
public class MonthlyQuickBillService {

    private static final String DEFAULT_ARRIVAL_TIME = "15:30";
    private static final String SYNTHETIC_LEAVE_REASON = "月度批量录入";

    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final StudentArrivalCheckinDao arrivalCheckinDao;
    private final StudentArrivalCheckinService arrivalCheckinService;
    private final StudentMealRecordService mealRecordService;
    private final StudentLeaveRecordDao leaveRecordDao;
    private final StudentLeaveRecordService leaveRecordService;
    private final CourseConsumptionRecordDao consumptionRecordDao;
    private final CourseConsumptionService courseConsumptionService;

    public MonthlyQuickBillService(StudentDao studentDao, TeachingUnitDao teachingUnitDao,
                                    StudentUnitEnrollmentDao enrollmentDao,
                                    StudentArrivalCheckinDao arrivalCheckinDao,
                                    StudentArrivalCheckinService arrivalCheckinService,
                                    StudentMealRecordService mealRecordService,
                                    StudentLeaveRecordDao leaveRecordDao,
                                    StudentLeaveRecordService leaveRecordService,
                                    CourseConsumptionRecordDao consumptionRecordDao,
                                    CourseConsumptionService courseConsumptionService) {
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.enrollmentDao = enrollmentDao;
        this.arrivalCheckinDao = arrivalCheckinDao;
        this.arrivalCheckinService = arrivalCheckinService;
        this.mealRecordService = mealRecordService;
        this.leaveRecordDao = leaveRecordDao;
        this.leaveRecordService = leaveRecordService;
        this.consumptionRecordDao = consumptionRecordDao;
        this.courseConsumptionService = courseConsumptionService;
    }

    public List<QuickBillStudentResponse> listStudents(Long institutionId, Long classRoomId) {
        TeachingUnit classRoom = requireClassRoomInInstitution(institutionId, classRoomId);
        return studentDao.findAllByTeachingUnitId(classRoom.id()).stream()
                .filter(Student::enrolled)
                .map(s -> new QuickBillStudentResponse(s.id(), s.name(), enrolledCourses(s.id())))
                .toList();
    }

    @Transactional
    public void submit(Long institutionId, Long studentId, QuickBillSubmitRequest request) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        TeachingUnit classRoom = teachingUnitDao.findById(student.teachingUnitId())
                .orElseThrow(() -> new NotFoundException("ClassRoom not found: " + student.teachingUnitId()));
        YearMonth month = YearMonth.parse(request.month());

        int weekdaysInMonth = countWeekdays(month);
        if (request.mealDays() + request.leaveDays() > weekdaysInMonth) {
            throw new InvalidQuickBillRequestException(
                    "用餐天数(" + request.mealDays() + ") + 请假天数(" + request.leaveDays()
                            + ") 超过了 " + month + " 的工作日总数(" + weekdaysInMonth + ")");
        }

        Long classTeacherId = classRoom.teacherId();

        // 同一个学生同一个月可能会被重新提交（改个数字、网络超时重试、前端误触重复点击）。
        // 先把上一次本工具自己写的记录清掉，再按新的数字重新分配，而不是在旧记录基础上
        // 继续叠加——否则改小一个数字重新生成，账单金额不会跟着变小，改大则会被重复计费。
        clearPreviousSubmission(classTeacherId, studentId, month);

        // 到校/请假是互斥的两类真实记录，都要占用日期池，新分配的日期不能跟任何一类
        // 已有的真实记录（不是刚清掉的那些合成记录）冲突。
        Set<LocalDate> occupied = new HashSet<>();
        occupied.addAll(arrivalCheckinDao
                .findAllByStudentIdAndDateRange(studentId, month.atDay(1), month.atEndOfMonth()).stream()
                .map(StudentArrivalCheckin::checkinDate)
                .toList());
        occupied.addAll(leaveRecordDao
                .findAllByStudentIdAndDateRange(studentId, month.atDay(1), month.atEndOfMonth()).stream()
                .map(StudentLeaveRecord::leaveDate)
                .toList());

        List<LocalDate> leaveDates = pickDates(month, request.leaveDays(), occupied,
                "请假天数(" + request.leaveDays() + ")");
        for (LocalDate date : leaveDates) {
            leaveRecordService.setLeave(classTeacherId, studentId, date, SYNTHETIC_LEAVE_REASON);
        }
        occupied.addAll(leaveDates);

        List<LocalDate> mealDates = pickDates(month, request.mealDays(), occupied,
                "用餐天数(" + request.mealDays() + ")");
        for (LocalDate date : mealDates) {
            arrivalCheckinService.setArrival(classTeacherId, studentId, date, DEFAULT_ARRIVAL_TIME);
            mealRecordService.setMeal(classTeacherId, studentId, date);
        }

        for (CourseConsumptionInput input : request.courseConsumptions()) {
            if (input.count() == 0) {
                continue;
            }
            recordCourseConsumption(institutionId, studentId, month, input);
        }
    }

    private void recordCourseConsumption(Long institutionId, Long studentId, YearMonth month,
                                          CourseConsumptionInput input) {
        TeachingUnit course = teachingUnitDao.findById(input.courseId())
                .orElseThrow(() -> new NotFoundException("Course not found: " + input.courseId()));
        if (!course.institutionId().equals(institutionId) || course.billingMode() != BillingMode.LESSON_COUNT) {
            throw new NotFoundException("Course not found: " + input.courseId());
        }
        // 消课记录没有能安全识别"是不是本工具自己写的"标记（用的就是课程真实 teacherId，
        // 跟老师手动点名消课写出来的记录长得一模一样），不像到校/请假那样能用固定哨兵值
        // 区分。所以这里不支持重新提交覆盖——如果这个学生这门课这个月已经有消课记录，直接
        // 拒绝，请去课外课消课页面调整，避免悄悄多算或者少算。
        boolean alreadySubmitted = consumptionRecordDao
                .findAllByStudentIdAndDateRange(studentId, month.atDay(1), month.atEndOfMonth()).stream()
                .anyMatch(r -> r.teachingUnitId().equals(course.id()));
        if (alreadySubmitted) {
            throw new InvalidQuickBillRequestException(
                    "该生「" + course.name() + "」" + month + "已经提交过消课记录，如需调整请前往课外课消课页面修改，不支持在这里重复提交");
        }
        List<LocalDate> courseDates = pickDates(month, input.count(), Set.of(),
                "课外课「" + course.name() + "」节数(" + input.count() + ")");
        for (LocalDate date : courseDates) {
            courseConsumptionService.recordBatchConsumption(course.teacherId(), course.id(), date,
                    List.of(studentId));
        }
    }

    // 到校/用餐/请假记录都用固定的哨兵值写入（DEFAULT_ARRIVAL_TIME / SYNTHETIC_LEAVE_REASON），
    // 真实的老师打卡/请假几乎不会凑巧写出一模一样的值，所以重新提交时可以安全地先把"上次是
    // 本工具自己写的"那些记录清掉，而不会动到任何真实数据。
    private void clearPreviousSubmission(Long classTeacherId, Long studentId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        arrivalCheckinDao.findAllByStudentIdAndDateRange(studentId, start, end).stream()
                .filter(a -> DEFAULT_ARRIVAL_TIME.equals(a.arrivedAt()))
                .forEach(a -> arrivalCheckinService.clearArrival(classTeacherId, studentId, a.checkinDate()));

        leaveRecordDao.findAllByStudentIdAndDateRange(studentId, start, end).stream()
                .filter(l -> SYNTHETIC_LEAVE_REASON.equals(l.reason()))
                .forEach(l -> leaveRecordService.clearLeave(classTeacherId, studentId, l.leaveDate()));
    }

    private List<QuickBillCourseResponse> enrolledCourses(Long studentId) {
        return enrollmentDao.findAllByStudentId(studentId).stream()
                .filter(StudentUnitEnrollment::active)
                .map(e -> teachingUnitDao.findById(e.teachingUnitId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(u -> u.billingMode() == BillingMode.LESSON_COUNT)
                .map(u -> new QuickBillCourseResponse(u.id(), u.name()))
                .toList();
    }

    private List<LocalDate> pickDates(YearMonth month, int count, Set<LocalDate> occupied, String label) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate cursor = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        while (result.size() < count && !cursor.isAfter(end)) {
            boolean isWeekday = cursor.getDayOfWeek() != DayOfWeek.SATURDAY
                    && cursor.getDayOfWeek() != DayOfWeek.SUNDAY;
            if (isWeekday && !occupied.contains(cursor)) {
                result.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        if (result.size() < count) {
            throw new InvalidQuickBillRequestException(month + " 剩余可用工作日不足以安排" + label);
        }
        return result;
    }

    private int countWeekdays(YearMonth month) {
        int count = 0;
        LocalDate cursor = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        while (!cursor.isAfter(end)) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private TeachingUnit requireClassRoomInInstitution(Long institutionId, Long classRoomId) {
        TeachingUnit unit = teachingUnitDao.findById(classRoomId)
                .orElseThrow(() -> new NotFoundException("ClassRoom not found: " + classRoomId));
        if (!unit.institutionId().equals(institutionId) || unit.billingMode() != BillingMode.MONTHLY) {
            throw new NotFoundException("ClassRoom not found: " + classRoomId);
        }
        return unit;
    }

    private Student requireStudentInInstitution(Long institutionId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (!student.institutionId().equals(institutionId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }
}
