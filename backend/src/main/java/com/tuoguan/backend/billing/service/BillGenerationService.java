package com.tuoguan.backend.billing.service;

import com.tuoguan.backend.admin.web.BatchSetPaidFailure;
import com.tuoguan.backend.admin.web.BatchSetPaidResult;
import com.tuoguan.backend.admin.web.BillOverviewRow;
import com.tuoguan.backend.admin.web.BillingRateNotConfiguredException;
import com.tuoguan.backend.admin.web.BulkBillGenerationFailure;
import com.tuoguan.backend.admin.web.BulkBillGenerationResult;
import com.tuoguan.backend.admin.web.ClassBillingRateRow;
import com.tuoguan.backend.admin.web.CourseConsumptionSummaryRow;
import com.tuoguan.backend.admin.web.RevenueSnapshot;
import com.tuoguan.backend.admin.web.UnpaidBillRow;
import com.tuoguan.backend.admin.web.UnpaidBillSummary;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.dao.MonthlyBillExtraFeeLineDao;
import com.tuoguan.backend.billing.dao.MonthlyBillExtraFeeLineDateDao;
import com.tuoguan.backend.billing.dao.MonthlyBillLeaveLineDao;
import com.tuoguan.backend.billing.dao.MonthlyBillMealLineDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.billing.domain.ClassBillingRate;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.domain.MonthlyBillLeaveLine;
import com.tuoguan.backend.billing.domain.MonthlyBillMealLine;
import com.tuoguan.backend.billing.domain.PaymentMethod;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.course.dao.CourseMealRecordDao;
import com.tuoguan.backend.course.domain.CourseMealRecord;
import com.tuoguan.backend.course.service.CourseAccountService;
import com.tuoguan.backend.course.web.ConsumptionCoverage;
import com.tuoguan.backend.kanban.dao.StudentMealRecordDao;
import com.tuoguan.backend.kanban.domain.StudentMealRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.service.StudentOverviewOrder;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BillGenerationService {

    private static final Logger log = LoggerFactory.getLogger(BillGenerationService.class);

    private final ClassBillingRateDao classBillingRateDao;
    private final CourseAccountService courseAccountService;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final MonthlyBillDao monthlyBillDao;
    private final MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao;
    private final MonthlyBillExtraFeeLineDateDao monthlyBillExtraFeeLineDateDao;
    private final MonthlyBillMealLineDao monthlyBillMealLineDao;
    private final MonthlyBillLeaveLineDao monthlyBillLeaveLineDao;
    private final StudentMealRecordDao studentMealRecordDao;
    private final CourseMealRecordDao courseMealRecordDao;
    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;
    private final StudentUnitEnrollmentDao enrollmentDao;
    private final InstitutionDao institutionDao;
    // 诊断 #33：批量生成（generateBillsForClass/generateBillsForInstitution）内部要调用
    // 本类自己的 generateBill，如果直接用 this.generateBill(...)，是类内自调用，Spring AOP
    // 代理拦截不到，@Transactional 对那次调用形同虚设——上一个学生账单写到一半失败，已经
    // 落库的部分不会回滚。注入一个指向自己的代理（必须 @Lazy，否则构造时自己还没创建完,
    // 会循环依赖），批量场景改成调用 self.generateBill(...)，让每个学生的账单生成重新
    // 经过代理、各自成为独立的事务边界，和单学生生成（Controller 调用，天然走代理）行为
    // 一致。
    private final BillGenerationService self;

    public BillGenerationService(ClassBillingRateDao classBillingRateDao,
                                  CourseAccountService courseAccountService,
                                  StudentLeaveRecordDao studentLeaveRecordDao, MonthlyBillDao monthlyBillDao,
                                  MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao,
                                  MonthlyBillExtraFeeLineDateDao monthlyBillExtraFeeLineDateDao,
                                  MonthlyBillMealLineDao monthlyBillMealLineDao,
                                  MonthlyBillLeaveLineDao monthlyBillLeaveLineDao,
                                  StudentMealRecordDao studentMealRecordDao, CourseMealRecordDao courseMealRecordDao,
                                  StudentDao studentDao,
                                  TeachingUnitDao teachingUnitDao, TeacherDao teacherDao,
                                  StudentUnitEnrollmentDao enrollmentDao, InstitutionDao institutionDao,
                                  @Lazy BillGenerationService self) {
        this.classBillingRateDao = classBillingRateDao;
        this.courseAccountService = courseAccountService;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.monthlyBillDao = monthlyBillDao;
        this.monthlyBillExtraFeeLineDao = monthlyBillExtraFeeLineDao;
        this.monthlyBillExtraFeeLineDateDao = monthlyBillExtraFeeLineDateDao;
        this.monthlyBillMealLineDao = monthlyBillMealLineDao;
        this.monthlyBillLeaveLineDao = monthlyBillLeaveLineDao;
        this.studentMealRecordDao = studentMealRecordDao;
        this.courseMealRecordDao = courseMealRecordDao;
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.teacherDao = teacherDao;
        this.enrollmentDao = enrollmentDao;
        this.institutionDao = institutionDao;
        this.self = self;
    }

    // 产品反馈：部分机构月初预收「下个月」托管费，同时后收「上个月」实际发生的餐费/
    // 课外课费，两者合并成一张账单（不是两次独立收款）。账单本身的 yearMonth 始终是
    // 管理员选的那个托管费所属月份；开启 billTuitionInAdvance 后，除了托管费单价仍按
    // 账单的 yearMonth 取值之外，其余一切依赖"这个月实际发生了什么"的数据（请假、
    // 用餐、课外课消课）全部改用上一个月。纯课外课学生（没有托管费这一项）也按同一个
    // 偏移量处理——这是机构统一的收款节奏，不是只跟托管费绑定的规则。
    private YearMonth resolveUsageMonth(Long institutionId, YearMonth billMonth) {
        Institution institution = institutionDao.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Institution not found: " + institutionId));
        return institution.billTuitionInAdvance() ? billMonth.minusMonths(1) : billMonth;
    }

    public Optional<ClassBillingRate> getBillingRate(Long institutionId, Long classRoomId) {
        requireTeachingUnitInInstitution(institutionId, classRoomId);
        return classBillingRateDao.findByTeachingUnitId(classRoomId);
    }

    public ClassBillingRate upsertBillingRate(Long institutionId, Long classRoomId, BigDecimal tuitionRatePerMonth,
                                               BigDecimal mealRatePerDay) {
        requireTeachingUnitInInstitution(institutionId, classRoomId);
        classBillingRateDao.upsert(institutionId, classRoomId, tuitionRatePerMonth, mealRatePerDay);
        return classBillingRateDao.findByTeachingUnitId(classRoomId)
                .orElseThrow(() -> new IllegalStateException("Billing rate not found after upsert: " + classRoomId));
    }

    public List<ClassBillingRateRow> listAllClassBillingRates(Long institutionId) {
        return findAllMonthlyUnits(institutionId).stream()
                .map(unit -> {
                    ClassBillingRate rate = classBillingRateDao.findByTeachingUnitId(unit.id()).orElse(null);
                    return new ClassBillingRateRow(unit.id(), unit.name(),
                            rate != null ? rate.tuitionRatePerMonth() : null,
                            rate != null ? rate.mealRatePerDay() : null);
                })
                .toList();
    }

    @Transactional
    public void bulkSetBillingRate(Long institutionId, BigDecimal tuitionRatePerMonth, BigDecimal mealRatePerDay) {
        for (TeachingUnit unit : findAllMonthlyUnits(institutionId)) {
            classBillingRateDao.upsert(institutionId, unit.id(), tuitionRatePerMonth, mealRatePerDay);
        }
    }

    public List<CourseConsumptionSummaryRow> listCourseConsumptionForMonth(Long institutionId, Long studentId,
                                                                            YearMonth month) {
        requireStudentInInstitution(institutionId, studentId);
        YearMonth usageMonth = resolveUsageMonth(institutionId, month);
        return computeCourseConsumptionRows(studentId, usageMonth, !isMonthAlreadyPaid(studentId, month));
    }

    // 这个月的账单是否已经标记"已缴费"（产品诊断 #54）：已缴费说明机构已经收过这笔钱，
    // 补充值抵扣兜底逻辑不应该再对它生效，否则会出现"已经收了现金，账单却被后续充值
    // 倒着免单"的双重优待。还没生成过账单（空）按"未缴费"处理，允许兜底逻辑生效。
    private boolean isMonthAlreadyPaid(Long studentId, YearMonth month) {
        return monthlyBillDao.findByStudentIdAndYearMonth(studentId, month).map(MonthlyBill::isPaid).orElse(false);
    }

    // 产品诊断 #02 统一方案：消课是否计费取决于当时是否被预充值余额覆盖，不再是
    // "只要发生过消课就全额计入账单"。覆盖判定的唯一权威来源是
    // CourseAccountService.classifyConsumptions——它对任何学生都适用，有托管班的学生
    // 从不充值所以天然恒为未覆盖，和改造前的行为完全一致，这里不需要再区分学生类型。
    //
    // applyCatchUp（产品诊断 #54）：消课当时没被覆盖，不代表生成账单这一刻还欠着——
    // 管理员完全可能在消课之后、生成账单之前又给学生补了充值。classifyConsumptions
    // 按"事件发生的时间顺序"回放，充值没法倒着覆盖它日期之前已经发生的消课，所以这种
    // "先欠费、后补值"的场景下，哪怕现在净余额已经转正，严格按时间顺序算出来的覆盖
    // 结果依然是"未覆盖"。这里再做一层兜底：对判定为"未覆盖"的消课，检查这门课
    // "全部历史净余额"（不分时间先后，充值合计-消课合计）是否已经补平——补平了就按 0
    // 计费，没补平就只收差额部分，消课次数和日期始终如实展示。只在这个月账单还没标记
    // 已缴费时生效（见 isMonthAlreadyPaid），避免已经收过现金的旧账单被重新生成时又被
    // 倒着免单。
    // package-private（非 private）：供 BillGenerationServiceCatchUpUnitTest 直接单测
    // 追缴夹断逻辑，不需要经过 generateBill 的完整 DB 依赖链。
    List<CourseConsumptionSummaryRow> computeCourseConsumptionRows(Long studentId, YearMonth month,
                                                                     boolean applyCatchUp) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<ConsumptionCoverage> coverageThisMonth = courseAccountService.classifyConsumptions(studentId).stream()
                .filter(c -> !c.record().consumptionDate().isBefore(start) && !c.record().consumptionDate().isAfter(end))
                .toList();
        Map<Long, List<ConsumptionCoverage>> byCourse = coverageThisMonth.stream()
                .collect(Collectors.groupingBy(c -> c.record().teachingUnitId()));

        List<CourseConsumptionSummaryRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<ConsumptionCoverage>> entry : byCourse.entrySet()) {
            List<ConsumptionCoverage> courseCoverage = entry.getValue();
            TeachingUnit unit = teachingUnitDao.findById(entry.getKey()).orElse(null);
            List<ConsumptionCoverage> billable = courseCoverage.stream()
                    .filter(c -> !c.coveredByBalance())
                    .sorted(Comparator.comparing(c -> c.record().consumptionDate()))
                    .toList();
            int coveredByBalanceCount = courseCoverage.size() - billable.size();
            List<LocalDate> billableDates = billable.stream().map(c -> c.record().consumptionDate()).toList();

            List<ConsumptionCoverage> stillOwed = billable;
            boolean settledByRecharge = false;
            if (applyCatchUp && !billable.isEmpty()) {
                int netBalance = courseAccountService.getNetBalance(studentId, entry.getKey());
                int stillOwedCount = Math.min(billable.size(), Math.max(0, -netBalance));
                settledByRecharge = stillOwedCount < billable.size();
                stillOwed = billable.subList(billable.size() - stillOwedCount, billable.size());
            }
            BigDecimal amount = stillOwed.stream().map(c -> c.record().priceSnapshot())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new CourseConsumptionSummaryRow(entry.getKey(), unit != null ? unit.name() : "-",
                    unit != null ? unit.pricePerLesson() : null, billable.size(), amount, coveredByBalanceCount,
                    billableDates, settledByRecharge));
        }
        return rows;
    }

    public List<MonthlyBill> listClassBills(Long institutionId, Long classRoomId, YearMonth month) {
        requireTeachingUnitInInstitution(institutionId, classRoomId);
        return monthlyBillDao.findAllByTeachingUnitIdAndYearMonth(classRoomId, month).stream()
                .map(this::enrich)
                .toList();
    }

    public MonthlyBill generateBill(Long institutionId, Long studentId, YearMonth month) {
        // self 而不是 this：同样是为了不让这次调用绕开 @Transactional 代理（诊断 #33）。
        return self.generateBill(institutionId, studentId, month, null);
    }

    @Transactional
    public MonthlyBill generateBill(Long institutionId, Long studentId, YearMonth month, BigDecimal tuitionOverride) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        Long teachingUnitId = student.teachingUnitId();

        // usageMonth：请假/用餐/课外课消课这些"实际发生过什么"的数据该去查哪个月，
        // 托管费单价本身始终按账单的 yearMonth（即 month 参数）取值，不受影响，
        // 详见 resolveUsageMonth 的说明。
        YearMonth usageMonth = resolveUsageMonth(institutionId, month);
        LocalDate start = usageMonth.atDay(1);
        LocalDate end = usageMonth.atEndOfMonth();
        int totalWeekdays = countWeekdays(start, end);
        // 请假天数不再用 countByStudentIdAndDateRange 直接数，而是先查出完整记录列表——
        // 既要算天数，也要把具体哪几天、什么原因拍成快照存进 monthly_bill_leave_line，
        // 跟用餐记录是同一套"生成即固定、重新生成才更新"的处理方式。
        List<StudentLeaveRecord> leaveRecords = studentLeaveRecordDao.findAllByStudentIdAndDateRange(studentId, start, end);
        int leaveDays = leaveRecords.size();
        int attendanceDays = Math.max(0, totalWeekdays - leaveDays);

        BigDecimal tuitionAmount = BigDecimal.ZERO;
        BigDecimal mealAmount = BigDecimal.ZERO;
        List<LocalDate> mealDates = List.of();
        if (teachingUnitId != null) {
            ClassBillingRate rate = classBillingRateDao.findByTeachingUnitId(teachingUnitId)
                    .orElseThrow(() -> new BillingRateNotConfiguredException("班级未配置计费单价"));
            tuitionAmount = tuitionOverride != null ? tuitionOverride : rate.tuitionRatePerMonth();
            // 餐费不再按出勤天数推算，改成按老师实际标记的用餐记录天数计算——出勤和用餐是
            // 两件独立的事。具体哪几天用餐会在下面拍成快照写进 monthly_bill_meal_line。
            mealDates = studentMealRecordDao.findAllByStudentIdAndDateRange(studentId, start, end).stream()
                    .map(StudentMealRecord::mealDate)
                    .toList();
            mealAmount = rate.mealRatePerDay().multiply(BigDecimal.valueOf(mealDates.size()));
        } else {
            // 纯课外课学生没有托管班、没有 ClassBillingRate 可用，餐费改按机构统一配置的
            // 单价计算（见 Institution.offCampusMealRatePerDay）；复用同一张
            // monthly_bill_meal_line 快照表，它本来就只挂 bill_id，不区分来源。只有当月
            // 真的产生了用餐记录才要求机构必须配置好单价，没用过这个功能的机构不受影响。
            mealDates = courseMealRecordDao.findAllByStudentIdAndDateRange(studentId, start, end).stream()
                    .map(CourseMealRecord::mealDate)
                    .toList();
            if (!mealDates.isEmpty()) {
                Institution institution = institutionDao.findById(institutionId)
                        .orElseThrow(() -> new NotFoundException("Institution not found: " + institutionId));
                BigDecimal offCampusMealRate = institution.offCampusMealRatePerDay();
                if (offCampusMealRate == null) {
                    throw new BillingRateNotConfiguredException("机构未配置课外课用餐单价");
                }
                mealAmount = offCampusMealRate.multiply(BigDecimal.valueOf(mealDates.size()));
            }
        }
        List<CourseConsumptionSummaryRow> extraFeeRows =
                computeCourseConsumptionRows(studentId, usageMonth, !isMonthAlreadyPaid(studentId, month));
        BigDecimal extraFeeTotal = extraFeeRows.stream().map(CourseConsumptionSummaryRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = tuitionAmount.add(mealAmount).add(extraFeeTotal);

        Long billId = monthlyBillDao.upsert(institutionId, studentId, teachingUnitId, month, totalWeekdays,
                leaveDays, attendanceDays, tuitionAmount, mealAmount, extraFeeTotal, totalAmount);
        monthlyBillExtraFeeLineDao.deleteAllByBillId(billId);
        // 本月消课全部被预充值覆盖的课程（lessonCount=0）不生成账单明细行——那是「费用管理」
        // 弹窗才需要展示的透明度信息，不是真实欠费，出现在账单明细里反而容易让人误解。
        extraFeeRows.stream()
                .filter(row -> row.lessonCount() > 0)
                .forEach(row -> {
                    Long lineId = monthlyBillExtraFeeLineDao.insert(billId, row.courseName(), row.pricePerLesson(),
                            row.lessonCount(), row.amount(), row.settledByRecharge());
                    row.billableDates().forEach(date -> monthlyBillExtraFeeLineDateDao.insert(lineId, date));
                });
        monthlyBillMealLineDao.deleteAllByBillId(billId);
        mealDates.forEach(date -> monthlyBillMealLineDao.insert(billId, date));
        monthlyBillLeaveLineDao.deleteAllByBillId(billId);
        leaveRecords.forEach(r -> monthlyBillLeaveLineDao.insert(billId, r.leaveDate(), r.reason()));

        return monthlyBillDao.findById(billId)
                .map(this::enrich)
                .orElseThrow(() -> new IllegalStateException("Bill not found after upsert: " + billId));
    }

    public List<MonthlyBill> generateBillsForClass(Long institutionId, Long classRoomId, YearMonth month) {
        TeachingUnit unit = requireTeachingUnitInInstitution(institutionId, classRoomId);
        return studentDao.findAllByTeachingUnitId(unit.id()).stream()
                .filter(Student::enrolled)
                .map(s -> self.generateBill(institutionId, s.id(), month))
                .toList();
    }

    // 生成范围和账单管理页当前筛选出来的学生列表完全一致（产品诊断 #55）：直接复用
    // getBillOverview 算出来的那批学生 id，而不是自己再重新遍历一遍全机构学生——这样
    // "看到哪些学生就生成哪些学生的账单"永远和列表展示口径保持一致，不会出现按班级/
    // 姓名筛选之后，背后却把没筛选出来的学生账单也重新生成了一遍的意外。classRoomId/
    // studentName/offCampusOnly 都不传时就是没有筛选，等价于覆盖全机构所有在读学生
    // （含没有托管班的纯课外课学生）。单个学生因班级未配置计费单价失败是预期中会发生
    // 的情况（管理员忘记配），跳过并记下原因，不影响同批次其它学生；除此之外的异常照常
    // 往外抛——那是真 bug，不该被悄悄吞掉。
    public BulkBillGenerationResult generateBillsForInstitution(Long institutionId, YearMonth month,
                                                                 Long classRoomId, String studentName,
                                                                 boolean offCampusOnly) {
        List<BulkBillGenerationFailure> failures = new ArrayList<>();
        int generatedCount = 0;
        for (BillOverviewRow row : getBillOverview(institutionId, month, classRoomId, studentName, offCampusOnly)) {
            try {
                self.generateBill(institutionId, row.studentId(), month);
                generatedCount++;
            } catch (BillingRateNotConfiguredException e) {
                // 这个失败只会体现在返回给前端的 failures 列表里，管理员不点开结果弹窗
                // 仔细看就完全没有留痕（产品诊断 #56）——打一条 warn，至少容器日志里
                // 能查到"谁、什么时候、哪个学生因为什么原因没生成成功"。
                log.warn("账单生成失败：institutionId={}, studentId={}, studentName={}, month={}, 原因={}",
                        institutionId, row.studentId(), row.studentName(), month, e.getMessage());
                failures.add(new BulkBillGenerationFailure(row.studentId(), row.studentName(), e.getMessage()));
            }
        }
        log.info("批量生成账单完成：institutionId={}, month={}, 成功={}, 失败={}",
                institutionId, month, generatedCount, failures.size());
        return new BulkBillGenerationResult(generatedCount, failures);
    }

    public MonthlyBill getBill(Long institutionId, Long billId) {
        MonthlyBill bill = monthlyBillDao.findById(billId)
                .orElseThrow(() -> new NotFoundException("Bill not found: " + billId));
        if (!bill.institutionId().equals(institutionId)) {
            throw new NotFoundException("Bill not found: " + billId);
        }
        return enrich(bill);
    }

    public MonthlyBill setBillPaid(Long institutionId, Long billId, boolean isPaid, PaymentMethod paymentMethod) {
        MonthlyBill bill = monthlyBillDao.findById(billId)
                .orElseThrow(() -> new NotFoundException("Bill not found: " + billId));
        if (!bill.institutionId().equals(institutionId)) {
            throw new NotFoundException("Bill not found: " + billId);
        }
        // 取消缴费时顺带清空收款方式（#14）：未缴费的账单记一个收款方式没有意义，避免
        // 下次重新标记已缴费时让人误以为沿用了上一次的渠道。
        monthlyBillDao.setPaid(billId, isPaid, isPaid ? paymentMethod : null);
        return monthlyBillDao.findById(billId)
                .map(this::enrich)
                .orElseThrow(() -> new IllegalStateException("Bill not found after update: " + billId));
    }

    // 批量标记已缴费（#14）：逐个复用 setBillPaid 的权限校验和落库逻辑，跨机构/不存在的
    // 账单 id 归为失败，不让整批请求因为一条脏数据就全部 500。
    public BatchSetPaidResult batchSetBillPaid(Long institutionId, List<Long> billIds, boolean isPaid,
                                                PaymentMethod paymentMethod) {
        int successCount = 0;
        List<BatchSetPaidFailure> failures = new ArrayList<>();
        for (Long billId : billIds) {
            try {
                setBillPaid(institutionId, billId, isPaid, paymentMethod);
                successCount++;
            } catch (NotFoundException e) {
                log.warn("批量标记已缴费失败：institutionId={}, billId={}, 原因={}",
                        institutionId, billId, e.getMessage());
                failures.add(new BatchSetPaidFailure(billId, e.getMessage()));
            }
        }
        log.info("批量标记已缴费完成：institutionId={}, isPaid={}, 成功={}, 失败={}",
                institutionId, isPaid, successCount, failures.size());
        return new BatchSetPaidResult(successCount, failures);
    }

    public List<BillOverviewRow> getBillOverview(Long institutionId, YearMonth month, Long classRoomId,
                                                  String studentName, boolean offCampusOnly) {
        if (classRoomId != null) {
            TeachingUnit unit = requireTeachingUnitInInstitution(institutionId, classRoomId);
            return buildOverviewRowsForUnit(unit, institutionId, studentName,
                    monthlyBillDao.findAllByTeachingUnitIdAndYearMonth(unit.id(), month).stream()
                            .collect(Collectors.toMap(MonthlyBill::studentId, b -> b)),
                    month.toString());
        }

        Map<Long, TeachingUnit> unitsById = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(TeachingUnit::id, u -> u));

        List<BillOverviewRow> rows = new ArrayList<>();
        for (Student student : sortForOverview(studentDao.findAllByInstitutionId(institutionId), studentName, unitsById)) {
            if (offCampusOnly && student.teachingUnitId() != null) {
                continue;
            }
            TeachingUnit unit = student.teachingUnitId() != null ? unitsById.get(student.teachingUnitId()) : null;
            String teacherName = unit != null
                    ? teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("—") : "—";
            // 账单按 student_id 直接查（而不是按 teaching_unit_id 遍历再反查），纯课外课
            // 学生的账单 teaching_unit_id 是 NULL，按单元遍历永远查不到——曾经导致这类学生
            // 明明已经生成了账单，账单管理总览却一直显示"未生成"。
            MonthlyBill bill = monthlyBillDao.findByStudentIdAndYearMonth(student.id(), month).orElse(null);
            rows.add(new BillOverviewRow(student.id(), student.name(), unit != null ? unit.id() : null,
                    unit != null ? unit.name() : "—", teacherName, bill != null ? bill.id() : null,
                    bill != null ? bill.totalAmount() : null, bill != null && bill.isPaid(),
                    bill != null ? bill.paymentMethod() : null, month.toString(), overdueDaysFor(bill)));
        }
        return rows;
    }

    // 学生总览/账单管理共用排序口径：托管学生按托管教师排，纯课外课学生按首门课程名排，
    // 见 StudentOverviewOrder 的说明。这里顺带做 enrolled 过滤和姓名模糊搜索。
    private List<Student> sortForOverview(List<Student> students, String studentName,
                                           Map<Long, TeachingUnit> unitsById) {
        List<Student> filtered = students.stream()
                .filter(Student::enrolled)
                .filter(s -> studentName == null || studentName.isBlank() || s.name().contains(studentName))
                .toList();
        Map<Long, String> courseNameByUnitId = unitsById.values().stream()
                .filter(u -> u.billingMode() == BillingMode.LESSON_COUNT)
                .collect(Collectors.toMap(TeachingUnit::id, TeachingUnit::name));
        Map<Long, String> firstCourseNameByStudentId =
                StudentOverviewOrder.computeFirstCourseNames(filtered, courseNameByUnitId, enrollmentDao);
        return filtered.stream()
                .sorted(StudentOverviewOrder.comparator(unitsById, firstCourseNameByStudentId, teacherDao))
                .toList();
    }

    private List<BillOverviewRow> buildOverviewRowsForUnit(TeachingUnit unit, Long institutionId, String studentName,
                                                             Map<Long, MonthlyBill> billsByStudentId,
                                                             String yearMonth) {
        String teacherName = teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("—");
        List<BillOverviewRow> rows = new ArrayList<>();
        for (Student student : studentsInUnitSortedByName(unit.id(), studentName)) {
            MonthlyBill bill = billsByStudentId.get(student.id());
            rows.add(new BillOverviewRow(student.id(), student.name(), unit.id(), unit.name(), teacherName,
                    bill != null ? bill.id() : null, bill != null ? bill.totalAmount() : null,
                    bill != null && bill.isPaid(), bill != null ? bill.paymentMethod() : null, yearMonth,
                    overdueDaysFor(bill)));
        }
        return rows;
    }

    // 单个班级内部都是同一个托管教师，分组/按教师排序没有意义，按学生姓名排序即可。
    private List<Student> studentsInUnitSortedByName(Long teachingUnitId, String studentName) {
        return studentDao.findAllByTeachingUnitId(teachingUnitId).stream()
                .filter(Student::enrolled)
                .filter(s -> studentName == null || studentName.isBlank() || s.name().contains(studentName))
                .sorted(Comparator.comparing(Student::name))
                .toList();
    }

    public List<BillOverviewRow> getBillOverviewAllMonths(Long institutionId, Long classRoomId, String studentName,
                                                            boolean offCampusOnly) {
        if (classRoomId != null) {
            TeachingUnit unit = requireTeachingUnitInInstitution(institutionId, classRoomId);
            return buildOverviewRowsAllMonthsForUnit(unit, studentName);
        }

        Map<Long, TeachingUnit> unitsById = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(TeachingUnit::id, u -> u));

        List<BillOverviewRow> rows = new ArrayList<>();
        for (Student student : sortForOverview(studentDao.findAllByInstitutionId(institutionId), studentName, unitsById)) {
            if (offCampusOnly && student.teachingUnitId() != null) {
                continue;
            }
            TeachingUnit unit = student.teachingUnitId() != null ? unitsById.get(student.teachingUnitId()) : null;
            String teacherName = unit != null
                    ? teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("—") : "—";
            // 同上：按 student_id 直接查全部账单，不按 teaching_unit_id 遍历——纯课外课
            // 学生的账单 teaching_unit_id 是 NULL。
            List<MonthlyBill> bills = monthlyBillDao.findAllByStudentId(student.id());
            if (bills == null || bills.isEmpty()) {
                rows.add(new BillOverviewRow(student.id(), student.name(), unit != null ? unit.id() : null,
                        unit != null ? unit.name() : "—", teacherName, null, null, false, null, "-", 0));
            } else {
                for (MonthlyBill bill : bills) {
                    rows.add(new BillOverviewRow(student.id(), student.name(), unit != null ? unit.id() : null,
                            unit != null ? unit.name() : "—", teacherName, bill.id(), bill.totalAmount(),
                            bill.isPaid(), bill.paymentMethod(), bill.yearMonth().toString(), overdueDaysFor(bill)));
                }
            }
        }
        return rows;
    }

    public UnpaidBillSummary getUnpaidBillSummary(Long institutionId) {
        // 产品诊断 #11：¥0.00 的账单（纯课外课学生当月消课全被充值覆盖时会生成）本质上
        // 不欠钱，混进"未缴费"提醒会把真正该催收的大额欠费淹没，这里额外排除掉——只影响
        // 这个提醒口径，账单管理页逐条列表不受影响。
        List<UnpaidBillRow> rows = getBillOverviewAllMonths(institutionId, null, null, false).stream()
                .filter(row -> row.billId() != null && !row.isPaid()
                        && row.totalAmount() != null && row.totalAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(BillOverviewRow::yearMonth).thenComparing(BillOverviewRow::studentName))
                .map(row -> new UnpaidBillRow(row.studentId(), row.studentName(), row.className(), row.yearMonth(),
                        row.totalAmount(), row.overdueDays()))
                .toList();
        BigDecimal total = rows.stream()
                .map(UnpaidBillRow::totalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UnpaidBillSummary(rows.size(), total, rows);
    }

    public RevenueSnapshot getRevenueSnapshot(Long institutionId) {
        YearMonth tuitionMonth = YearMonth.now().minusMonths(1);
        YearMonth consumptionMonth = YearMonth.now();

        List<BillOverviewRow> lastMonthRows = getBillOverview(institutionId, tuitionMonth, null, null, false);
        BigDecimal tuitionBilled = lastMonthRows.stream()
                .filter(row -> row.billId() != null)
                .map(BillOverviewRow::totalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tuitionCollected = lastMonthRows.stream()
                .filter(row -> row.billId() != null && row.isPaid())
                .map(BillOverviewRow::totalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int offCampusConsumptionCount = courseAccountService.countConsumptionsInMonth(institutionId, consumptionMonth);

        return new RevenueSnapshot(tuitionMonth.toString(), tuitionBilled, tuitionCollected,
                consumptionMonth.toString(), offCampusConsumptionCount);
    }

    private List<BillOverviewRow> buildOverviewRowsAllMonthsForUnit(TeachingUnit unit, String studentName) {
        String teacherName = teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("—");
        Map<Long, List<MonthlyBill>> billsByStudentId = monthlyBillDao.findAllByTeachingUnitId(unit.id())
                .stream().collect(Collectors.groupingBy(MonthlyBill::studentId));
        List<BillOverviewRow> rows = new ArrayList<>();
        for (Student student : studentsInUnitSortedByName(unit.id(), studentName)) {
            List<MonthlyBill> bills = billsByStudentId.get(student.id());
            if (bills == null || bills.isEmpty()) {
                rows.add(new BillOverviewRow(student.id(), student.name(), unit.id(), unit.name(), teacherName,
                        null, null, false, null, "-", 0));
            } else {
                for (MonthlyBill bill : bills) {
                    rows.add(new BillOverviewRow(student.id(), student.name(), unit.id(), unit.name(), teacherName,
                            bill.id(), bill.totalAmount(), bill.isPaid(), bill.paymentMethod(),
                            bill.yearMonth().toString(), overdueDaysFor(bill)));
                }
            }
        }
        return rows;
    }

    // 产品诊断 #14：账单没有独立的到期日字段，这里约定到期日 = 账单所属月份的月底，
    // 未缴费且已过月底才算逾期；没有账单（bill==null）或已缴费恒为 0。
    private int overdueDaysFor(MonthlyBill bill) {
        if (bill == null || bill.isPaid()) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(bill.yearMonth().atEndOfMonth(), LocalDate.now());
        return (int) Math.max(0, days);
    }

    private int countWeekdays(LocalDate start, LocalDate end) {
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    private MonthlyBill enrich(MonthlyBill bill) {
        List<com.tuoguan.backend.billing.domain.MonthlyBillExtraFeeLine> lines =
                monthlyBillExtraFeeLineDao.findAllByBillId(bill.id());
        List<LocalDate> mealDates = monthlyBillMealLineDao.findAllByBillId(bill.id()).stream()
                .map(MonthlyBillMealLine::mealDate)
                .toList();
        List<MonthlyBillLeaveLine> leaveLines = monthlyBillLeaveLineDao.findAllByBillId(bill.id());
        return new MonthlyBill(bill.id(), bill.institutionId(), bill.studentId(), bill.teachingUnitId(),
                bill.yearMonth(), bill.totalWeekdays(), bill.leaveDays(), bill.attendanceDays(),
                bill.tuitionAmount(), bill.mealAmount(), bill.extraFeeTotal(), bill.totalAmount(), bill.isPaid(),
                bill.paymentMethod(), bill.generatedAt(), lines, mealDates, leaveLines);
    }

    private Student requireStudentInInstitution(Long institutionId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (!student.institutionId().equals(institutionId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }

    private TeachingUnit requireTeachingUnitInInstitution(Long institutionId, Long teachingUnitId) {
        TeachingUnit unit = teachingUnitDao.findById(teachingUnitId)
                .orElseThrow(() -> new NotFoundException("ClassRoom not found: " + teachingUnitId));
        if (!unit.institutionId().equals(institutionId)) {
            throw new NotFoundException("ClassRoom not found: " + teachingUnitId);
        }
        return unit;
    }

    private List<TeachingUnit> findAllMonthlyUnits(Long institutionId) {
        return teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .filter(u -> u.billingMode() == BillingMode.MONTHLY)
                .toList();
    }
}
