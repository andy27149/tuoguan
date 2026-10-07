package com.tuoguan.backend.billing.service;

import com.tuoguan.backend.admin.web.BillOverviewRow;
import com.tuoguan.backend.admin.web.BillingRateNotConfiguredException;
import com.tuoguan.backend.admin.web.ClassBillingRateRow;
import com.tuoguan.backend.admin.web.CourseConsumptionSummaryRow;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.dao.MonthlyBillExtraFeeLineDao;
import com.tuoguan.backend.billing.dao.MonthlyBillLeaveLineDao;
import com.tuoguan.backend.billing.dao.MonthlyBillMealLineDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.billing.domain.ClassBillingRate;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.domain.MonthlyBillLeaveLine;
import com.tuoguan.backend.billing.domain.MonthlyBillMealLine;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.course.service.CourseAccountService;
import com.tuoguan.backend.course.web.ConsumptionCoverage;
import com.tuoguan.backend.kanban.dao.StudentMealRecordDao;
import com.tuoguan.backend.kanban.domain.StudentMealRecord;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BillGenerationService {

    private final ClassBillingRateDao classBillingRateDao;
    private final CourseAccountService courseAccountService;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final MonthlyBillDao monthlyBillDao;
    private final MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao;
    private final MonthlyBillMealLineDao monthlyBillMealLineDao;
    private final MonthlyBillLeaveLineDao monthlyBillLeaveLineDao;
    private final StudentMealRecordDao studentMealRecordDao;
    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;

    public BillGenerationService(ClassBillingRateDao classBillingRateDao,
                                  CourseAccountService courseAccountService,
                                  StudentLeaveRecordDao studentLeaveRecordDao, MonthlyBillDao monthlyBillDao,
                                  MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao,
                                  MonthlyBillMealLineDao monthlyBillMealLineDao,
                                  MonthlyBillLeaveLineDao monthlyBillLeaveLineDao,
                                  StudentMealRecordDao studentMealRecordDao, StudentDao studentDao,
                                  TeachingUnitDao teachingUnitDao, TeacherDao teacherDao) {
        this.classBillingRateDao = classBillingRateDao;
        this.courseAccountService = courseAccountService;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.monthlyBillDao = monthlyBillDao;
        this.monthlyBillExtraFeeLineDao = monthlyBillExtraFeeLineDao;
        this.monthlyBillMealLineDao = monthlyBillMealLineDao;
        this.monthlyBillLeaveLineDao = monthlyBillLeaveLineDao;
        this.studentMealRecordDao = studentMealRecordDao;
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.teacherDao = teacherDao;
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
        return computeCourseConsumptionRows(studentId, month);
    }

    // 产品诊断 #02 统一方案：消课是否计费取决于当时是否被预充值余额覆盖，不再是
    // "只要发生过消课就全额计入账单"。覆盖判定的唯一权威来源是
    // CourseAccountService.classifyConsumptions——它对任何学生都适用，有托管班的学生
    // 从不充值所以天然恒为未覆盖，和改造前的行为完全一致，这里不需要再区分学生类型。
    private List<CourseConsumptionSummaryRow> computeCourseConsumptionRows(Long studentId, YearMonth month) {
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
            List<ConsumptionCoverage> billable = courseCoverage.stream().filter(c -> !c.coveredByBalance()).toList();
            int coveredByBalanceCount = courseCoverage.size() - billable.size();
            BigDecimal amount = billable.stream().map(c -> c.record().priceSnapshot())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new CourseConsumptionSummaryRow(entry.getKey(), unit != null ? unit.name() : "-",
                    unit != null ? unit.pricePerLesson() : null, billable.size(), amount, coveredByBalanceCount));
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
        return generateBill(institutionId, studentId, month, null);
    }

    @Transactional
    public MonthlyBill generateBill(Long institutionId, Long studentId, YearMonth month, BigDecimal tuitionOverride) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        Long teachingUnitId = student.teachingUnitId();

        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
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
        }
        List<CourseConsumptionSummaryRow> extraFeeRows = computeCourseConsumptionRows(studentId, month);
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
                .forEach(row -> monthlyBillExtraFeeLineDao.insert(billId, row.courseName(), row.pricePerLesson(),
                        row.lessonCount(), row.amount()));
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
                .map(s -> generateBill(institutionId, s.id(), month))
                .toList();
    }

    public MonthlyBill getBill(Long institutionId, Long billId) {
        MonthlyBill bill = monthlyBillDao.findById(billId)
                .orElseThrow(() -> new NotFoundException("Bill not found: " + billId));
        if (!bill.institutionId().equals(institutionId)) {
            throw new NotFoundException("Bill not found: " + billId);
        }
        return enrich(bill);
    }

    public MonthlyBill setBillPaid(Long institutionId, Long billId, boolean isPaid) {
        MonthlyBill bill = monthlyBillDao.findById(billId)
                .orElseThrow(() -> new NotFoundException("Bill not found: " + billId));
        if (!bill.institutionId().equals(institutionId)) {
            throw new NotFoundException("Bill not found: " + billId);
        }
        monthlyBillDao.setPaid(billId, isPaid);
        return monthlyBillDao.findById(billId)
                .map(this::enrich)
                .orElseThrow(() -> new IllegalStateException("Bill not found after update: " + billId));
    }

    public List<BillOverviewRow> getBillOverview(Long institutionId, YearMonth month, Long classRoomId,
                                                  String studentName) {
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
        for (Student student : studentDao.findAllByInstitutionId(institutionId)) {
            if (!student.enrolled()) {
                continue;
            }
            if (studentName != null && !studentName.isBlank() && !student.name().contains(studentName)) {
                continue;
            }
            TeachingUnit unit = student.teachingUnitId() != null ? unitsById.get(student.teachingUnitId()) : null;
            String teacherName = unit != null
                    ? teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("-") : "-";
            // 账单按 student_id 直接查（而不是按 teaching_unit_id 遍历再反查），纯课外课
            // 学生的账单 teaching_unit_id 是 NULL，按单元遍历永远查不到——曾经导致这类学生
            // 明明已经生成了账单，账单管理总览却一直显示"未生成"。
            MonthlyBill bill = monthlyBillDao.findByStudentIdAndYearMonth(student.id(), month).orElse(null);
            rows.add(new BillOverviewRow(student.id(), student.name(), unit != null ? unit.id() : null,
                    unit != null ? unit.name() : "—", teacherName, bill != null ? bill.id() : null,
                    bill != null ? bill.totalAmount() : null, bill != null && bill.isPaid(), month.toString()));
        }
        return rows;
    }

    private List<BillOverviewRow> buildOverviewRowsForUnit(TeachingUnit unit, Long institutionId, String studentName,
                                                             Map<Long, MonthlyBill> billsByStudentId,
                                                             String yearMonth) {
        String teacherName = teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("-");
        List<BillOverviewRow> rows = new ArrayList<>();
        for (Student student : studentDao.findAllByTeachingUnitId(unit.id())) {
            if (!student.enrolled()) {
                continue;
            }
            if (studentName != null && !studentName.isBlank() && !student.name().contains(studentName)) {
                continue;
            }
            MonthlyBill bill = billsByStudentId.get(student.id());
            rows.add(new BillOverviewRow(student.id(), student.name(), unit.id(), unit.name(), teacherName,
                    bill != null ? bill.id() : null, bill != null ? bill.totalAmount() : null,
                    bill != null && bill.isPaid(), yearMonth));
        }
        return rows;
    }

    public List<BillOverviewRow> getBillOverviewAllMonths(Long institutionId, Long classRoomId, String studentName) {
        if (classRoomId != null) {
            TeachingUnit unit = requireTeachingUnitInInstitution(institutionId, classRoomId);
            return buildOverviewRowsAllMonthsForUnit(unit, studentName);
        }

        Map<Long, TeachingUnit> unitsById = teachingUnitDao.findAllByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(TeachingUnit::id, u -> u));

        List<BillOverviewRow> rows = new ArrayList<>();
        for (Student student : studentDao.findAllByInstitutionId(institutionId)) {
            if (!student.enrolled()) {
                continue;
            }
            if (studentName != null && !studentName.isBlank() && !student.name().contains(studentName)) {
                continue;
            }
            TeachingUnit unit = student.teachingUnitId() != null ? unitsById.get(student.teachingUnitId()) : null;
            String teacherName = unit != null
                    ? teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("-") : "-";
            // 同上：按 student_id 直接查全部账单，不按 teaching_unit_id 遍历——纯课外课
            // 学生的账单 teaching_unit_id 是 NULL。
            List<MonthlyBill> bills = monthlyBillDao.findAllByStudentId(student.id());
            if (bills == null || bills.isEmpty()) {
                rows.add(new BillOverviewRow(student.id(), student.name(), unit != null ? unit.id() : null,
                        unit != null ? unit.name() : "—", teacherName, null, null, false, "-"));
            } else {
                for (MonthlyBill bill : bills) {
                    rows.add(new BillOverviewRow(student.id(), student.name(), unit != null ? unit.id() : null,
                            unit != null ? unit.name() : "—", teacherName, bill.id(), bill.totalAmount(),
                            bill.isPaid(), bill.yearMonth().toString()));
                }
            }
        }
        return rows;
    }

    private List<BillOverviewRow> buildOverviewRowsAllMonthsForUnit(TeachingUnit unit, String studentName) {
        String teacherName = teacherDao.findById(unit.teacherId()).map(Teacher::name).orElse("-");
        Map<Long, List<MonthlyBill>> billsByStudentId = monthlyBillDao.findAllByTeachingUnitId(unit.id())
                .stream().collect(Collectors.groupingBy(MonthlyBill::studentId));
        List<BillOverviewRow> rows = new ArrayList<>();
        for (Student student : studentDao.findAllByTeachingUnitId(unit.id())) {
            if (!student.enrolled()) {
                continue;
            }
            if (studentName != null && !studentName.isBlank() && !student.name().contains(studentName)) {
                continue;
            }
            List<MonthlyBill> bills = billsByStudentId.get(student.id());
            if (bills == null || bills.isEmpty()) {
                rows.add(new BillOverviewRow(student.id(), student.name(), unit.id(), unit.name(), teacherName,
                        null, null, false, "-"));
            } else {
                for (MonthlyBill bill : bills) {
                    rows.add(new BillOverviewRow(student.id(), student.name(), unit.id(), unit.name(), teacherName,
                            bill.id(), bill.totalAmount(), bill.isPaid(), bill.yearMonth().toString()));
                }
            }
        }
        return rows;
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
                bill.generatedAt(), lines, mealDates, leaveLines);
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
