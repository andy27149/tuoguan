package com.tuoguan.backend.monthlyquickbill.service;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.service.CourseConsumptionService;
import com.tuoguan.backend.kanban.dao.StudentArrivalCheckinDao;
import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;
import com.tuoguan.backend.kanban.service.StudentArrivalCheckinService;
import com.tuoguan.backend.kanban.service.StudentLeaveRecordService;
import com.tuoguan.backend.kanban.service.StudentMealRecordService;
import com.tuoguan.backend.monthlyquickbill.dao.MonthlyQuickBillRecordDao;
import com.tuoguan.backend.monthlyquickbill.domain.MonthlyQuickBillRecord;
import com.tuoguan.backend.monthlyquickbill.domain.QuickBillRecordType;
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
// 不与已有记录冲突。
@Service
public class MonthlyQuickBillService {

    private static final String DEFAULT_ARRIVAL_TIME = "15:30";
    private static final String DEFAULT_LEAVE_REASON = "月度批量录入";

    private final InstitutionDao institutionDao;
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
    private final MonthlyQuickBillRecordDao quickBillRecordDao;

    public MonthlyQuickBillService(InstitutionDao institutionDao, StudentDao studentDao,
                                    TeachingUnitDao teachingUnitDao,
                                    StudentUnitEnrollmentDao enrollmentDao,
                                    StudentArrivalCheckinDao arrivalCheckinDao,
                                    StudentArrivalCheckinService arrivalCheckinService,
                                    StudentMealRecordService mealRecordService,
                                    StudentLeaveRecordDao leaveRecordDao,
                                    StudentLeaveRecordService leaveRecordService,
                                    CourseConsumptionRecordDao consumptionRecordDao,
                                    CourseConsumptionService courseConsumptionService,
                                    MonthlyQuickBillRecordDao quickBillRecordDao) {
        this.institutionDao = institutionDao;
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
        this.quickBillRecordDao = quickBillRecordDao;
    }

    // billMonth 跟 submit() 收到的 request.month 是同一个意思（账单月份标签，不是记录
    // 实际落在哪个自然月）——这里同样要换算成 usageMonth，查的才是运营人员下次提交时
    // 实际会读写的那个月，预填出来的数字才对得上。
    public List<QuickBillStudentResponse> listStudents(Long institutionId, Long classRoomId, YearMonth billMonth) {
        TeachingUnit classRoom = requireClassRoomInInstitution(institutionId, classRoomId);
        YearMonth usageMonth = resolveUsageMonth(institutionId, billMonth);
        LocalDate start = usageMonth.atDay(1);
        LocalDate end = usageMonth.atEndOfMonth();

        return studentDao.findAllByTeachingUnitId(classRoom.id()).stream()
                .filter(Student::enrolled)
                .map(s -> {
                    List<MonthlyQuickBillRecord> records = quickBillRecordDao
                            .findAllByStudentIdAndDateRange(s.id(), start, end);
                    int mealDays = (int) records.stream()
                            .filter(r -> r.recordType() == QuickBillRecordType.ARRIVAL_MEAL).count();
                    int leaveDays = (int) records.stream()
                            .filter(r -> r.recordType() == QuickBillRecordType.LEAVE).count();
                    return new QuickBillStudentResponse(s.id(), s.name(), mealDays, leaveDays,
                            enrolledCourses(s.id(), start, end));
                })
                .toList();
    }

    @Transactional
    public void submit(Long institutionId, Long studentId, QuickBillSubmitRequest request) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        TeachingUnit classRoom = teachingUnitDao.findById(student.teachingUnitId())
                .orElseThrow(() -> new NotFoundException("ClassRoom not found: " + student.teachingUnitId()));

        // request.month 是运营人员心里"这期账单对应哪个月"的标签（前端月份选择器上显示
        // 的那个月），不一定是记录该落在哪个自然月——机构开了"托管费提前收"（见
        // BillGenerationService.resolveUsageMonth 的同一条规则）时，这期账单的学费是
        // 上个月就收了，这个月生成账单实际结算的是"上个月"的真实出勤/用餐/消课数据。这里
        // 必须和账单引擎用同一套月份换算，不然运营人员填的数字会被写进引擎根本不读的那个
        // 月份，表面上提交成功、账单却纹丝不动（live-tested 2026-11-13 时真实踩到过）。
        YearMonth usageMonth = resolveUsageMonth(institutionId, YearMonth.parse(request.month()));

        int weekdaysInMonth = countWeekdays(usageMonth);
        if (request.mealDays() + request.leaveDays() > weekdaysInMonth) {
            throw new InvalidQuickBillRequestException(
                    "用餐天数(" + request.mealDays() + ") + 请假天数(" + request.leaveDays()
                            + ") 超过了 " + usageMonth + " 的工作日总数(" + weekdaysInMonth + ")");
        }

        Long classTeacherId = classRoom.teacherId();

        // 同一个学生同一个月可能会被重新提交（改个数字、网络超时重试、前端误触重复点击）。
        // 先把上一次本工具自己写的记录清掉，再按新的数字重新分配，而不是在旧记录基础上
        // 继续叠加——否则改小一个数字重新生成，账单金额不会跟着变小，改大则会被重复计费。
        clearPreviousSubmission(classTeacherId, studentId, usageMonth);

        // 到校/请假是互斥的两类真实记录，都要占用日期池，新分配的日期不能跟任何一类
        // 已有的真实记录（不是刚清掉的那些合成记录）冲突。
        Set<LocalDate> occupied = new HashSet<>();
        occupied.addAll(arrivalCheckinDao
                .findAllByStudentIdAndDateRange(studentId, usageMonth.atDay(1), usageMonth.atEndOfMonth()).stream()
                .map(StudentArrivalCheckin::checkinDate)
                .toList());
        occupied.addAll(leaveRecordDao
                .findAllByStudentIdAndDateRange(studentId, usageMonth.atDay(1), usageMonth.atEndOfMonth()).stream()
                .map(StudentLeaveRecord::leaveDate)
                .toList());

        List<LocalDate> leaveDates = pickDates(usageMonth, request.leaveDays(), occupied,
                "请假天数(" + request.leaveDays() + ")");
        for (LocalDate date : leaveDates) {
            leaveRecordService.setLeave(classTeacherId, studentId, date, DEFAULT_LEAVE_REASON);
            quickBillRecordDao.insert(institutionId, studentId, QuickBillRecordType.LEAVE, date);
        }
        occupied.addAll(leaveDates);

        List<LocalDate> mealDates = pickDates(usageMonth, request.mealDays(), occupied,
                "用餐天数(" + request.mealDays() + ")");
        for (LocalDate date : mealDates) {
            arrivalCheckinService.setArrival(classTeacherId, studentId, date, DEFAULT_ARRIVAL_TIME);
            mealRecordService.setMeal(classTeacherId, studentId, date);
            quickBillRecordDao.insert(institutionId, studentId, QuickBillRecordType.ARRIVAL_MEAL, date);
        }

        for (CourseConsumptionInput input : request.courseConsumptions()) {
            if (input.count() == 0) {
                continue;
            }
            recordCourseConsumption(institutionId, studentId, usageMonth, input);
        }
    }

    // 跟 BillGenerationService.resolveUsageMonth 保持同一条规则，故意不直接依赖那个类的
    // private 方法——两边各自维护一份一行逻辑，比为了复用把这个新模块的依赖伸到计费服务
    // 内部实现细节上更安全；BillGenerationService 是跑在生产、力达托管在用的真实计费代码，
    // 不想让这次新加的功能跟它产生除了"读同一张表"以外的耦合。
    private YearMonth resolveUsageMonth(Long institutionId, YearMonth billMonth) {
        Institution institution = institutionDao.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Institution not found: " + institutionId));
        return institution.billTuitionInAdvance() ? billMonth.minusMonths(1) : billMonth;
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

    // 靠猜（固定到校时间/固定请假原因）识别"是不是本工具自己写的"曾经出过真实事故：
    // live测试时机构种子数据里凑巧也用了同一个到校时间字符串，被误判成"自己写的"直接
    // 清掉，删了一条真实数据。现在靠 monthly_quick_bill_record 这张专门的记账表——只有
    // 这里插入过的 (学生, 日期) 才是本工具自己写的，不靠猜值。
    private void clearPreviousSubmission(Long classTeacherId, Long studentId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        for (MonthlyQuickBillRecord record : quickBillRecordDao.findAllByStudentIdAndDateRange(studentId, start, end)) {
            if (record.recordType() == QuickBillRecordType.ARRIVAL_MEAL) {
                arrivalCheckinService.clearArrival(classTeacherId, studentId, record.recordDate());
            } else {
                leaveRecordService.clearLeave(classTeacherId, studentId, record.recordDate());
            }
        }
        quickBillRecordDao.deleteAllByStudentIdAndDateRange(studentId, start, end);
    }

    private List<QuickBillCourseResponse> enrolledCourses(Long studentId, LocalDate usageMonthStart,
                                                           LocalDate usageMonthEnd) {
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao
                .findAllByStudentIdAndDateRange(studentId, usageMonthStart, usageMonthEnd);
        return enrollmentDao.findAllByStudentId(studentId).stream()
                .filter(StudentUnitEnrollment::active)
                .map(e -> teachingUnitDao.findById(e.teachingUnitId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(u -> u.billingMode() == BillingMode.LESSON_COUNT)
                .map(u -> new QuickBillCourseResponse(u.id(), u.name(),
                        (int) consumptions.stream().filter(r -> r.teachingUnitId().equals(u.id())).count()))
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
