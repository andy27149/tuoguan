package com.tuoguan.backend.billing.service;

import com.tuoguan.backend.admin.web.BillOverviewRow;
import com.tuoguan.backend.admin.web.BillingRateNotConfiguredException;
import com.tuoguan.backend.admin.web.ClassBillingRateRow;
import com.tuoguan.backend.admin.web.CourseConsumptionSummaryRow;
import com.tuoguan.backend.admin.web.InvalidLeaveDateException;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.dao.MonthlyBillExtraFeeLineDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.billing.domain.ClassBillingRate;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
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
    private final CourseConsumptionRecordDao courseConsumptionRecordDao;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final MonthlyBillDao monthlyBillDao;
    private final MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao;
    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;

    public BillGenerationService(ClassBillingRateDao classBillingRateDao,
                                  CourseConsumptionRecordDao courseConsumptionRecordDao,
                                  StudentLeaveRecordDao studentLeaveRecordDao, MonthlyBillDao monthlyBillDao,
                                  MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao, StudentDao studentDao,
                                  TeachingUnitDao teachingUnitDao, TeacherDao teacherDao) {
        this.classBillingRateDao = classBillingRateDao;
        this.courseConsumptionRecordDao = courseConsumptionRecordDao;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.monthlyBillDao = monthlyBillDao;
        this.monthlyBillExtraFeeLineDao = monthlyBillExtraFeeLineDao;
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

    private List<CourseConsumptionSummaryRow> computeCourseConsumptionRows(Long studentId, YearMonth month) {
        List<CourseConsumptionRecord> records = courseConsumptionRecordDao
                .findAllByStudentIdAndDateRange(studentId, month.atDay(1), month.atEndOfMonth());
        Map<Long, List<CourseConsumptionRecord>> byCourse = records.stream()
                .collect(Collectors.groupingBy(CourseConsumptionRecord::teachingUnitId));
        List<CourseConsumptionSummaryRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<CourseConsumptionRecord>> entry : byCourse.entrySet()) {
            List<CourseConsumptionRecord> courseRecords = entry.getValue();
            TeachingUnit unit = teachingUnitDao.findById(entry.getKey()).orElse(null);
            BigDecimal amount = courseRecords.stream().map(CourseConsumptionRecord::priceSnapshot)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rows.add(new CourseConsumptionSummaryRow(entry.getKey(), unit != null ? unit.name() : "-",
                    unit != null ? unit.pricePerLesson() : null, courseRecords.size(), amount));
        }
        return rows;
    }

    public List<StudentLeaveRecord> listLeaveRecords(Long institutionId, Long studentId, YearMonth month) {
        requireStudentInInstitution(institutionId, studentId);
        return studentLeaveRecordDao.findAllByStudentIdAndDateRange(studentId, month.atDay(1), month.atEndOfMonth());
    }

    public List<StudentLeaveRecord> registerLeaveRange(Long institutionId, Long studentId, LocalDate startDate,
                                                         LocalDate endDate, String reason) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        requireValidRange(startDate, endDate);
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            studentLeaveRecordDao.upsert(institutionId, studentId, student.teachingUnitId(), d, reason);
        }
        return studentLeaveRecordDao.findAllByStudentIdAndDateRange(studentId, startDate, endDate);
    }

    public void cancelLeave(Long institutionId, Long studentId, LocalDate date) {
        requireStudentInInstitution(institutionId, studentId);
        studentLeaveRecordDao.deleteByStudentIdAndDate(studentId, date);
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
        int leaveDays = studentLeaveRecordDao.countByStudentIdAndDateRange(studentId, start, end);
        int attendanceDays = Math.max(0, totalWeekdays - leaveDays);

        BigDecimal tuitionAmount = BigDecimal.ZERO;
        BigDecimal mealAmount = BigDecimal.ZERO;
        if (teachingUnitId != null) {
            ClassBillingRate rate = classBillingRateDao.findByTeachingUnitId(teachingUnitId)
                    .orElseThrow(() -> new BillingRateNotConfiguredException("班级未配置计费单价"));
            tuitionAmount = tuitionOverride != null ? tuitionOverride : rate.tuitionRatePerMonth();
            mealAmount = rate.mealRatePerDay().multiply(BigDecimal.valueOf(attendanceDays));
        }
        List<CourseConsumptionSummaryRow> extraFeeRows = computeCourseConsumptionRows(studentId, month);
        BigDecimal extraFeeTotal = extraFeeRows.stream().map(CourseConsumptionSummaryRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = tuitionAmount.add(mealAmount).add(extraFeeTotal);

        Long billId = monthlyBillDao.upsert(institutionId, studentId, teachingUnitId, month, totalWeekdays,
                leaveDays, attendanceDays, tuitionAmount, mealAmount, extraFeeTotal, totalAmount);
        monthlyBillExtraFeeLineDao.deleteAllByBillId(billId);
        extraFeeRows.forEach(row -> monthlyBillExtraFeeLineDao.insert(billId, row.courseName(), row.pricePerLesson(),
                row.lessonCount(), row.amount()));

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
        Map<Long, MonthlyBill> billsByStudentId = new java.util.HashMap<>();
        for (TeachingUnit unit : unitsById.values()) {
            for (MonthlyBill bill : monthlyBillDao.findAllByTeachingUnitIdAndYearMonth(unit.id(), month)) {
                billsByStudentId.put(bill.studentId(), bill);
            }
        }

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
            MonthlyBill bill = billsByStudentId.get(student.id());
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
        Map<Long, List<MonthlyBill>> billsByStudentId = new java.util.HashMap<>();
        for (TeachingUnit unit : unitsById.values()) {
            for (MonthlyBill bill : monthlyBillDao.findAllByTeachingUnitId(unit.id())) {
                billsByStudentId.computeIfAbsent(bill.studentId(), k -> new ArrayList<>()).add(bill);
            }
        }

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
            List<MonthlyBill> bills = billsByStudentId.get(student.id());
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
        return new MonthlyBill(bill.id(), bill.institutionId(), bill.studentId(), bill.teachingUnitId(),
                bill.yearMonth(), bill.totalWeekdays(), bill.leaveDays(), bill.attendanceDays(),
                bill.tuitionAmount(), bill.mealAmount(), bill.extraFeeTotal(), bill.totalAmount(), bill.isPaid(),
                bill.generatedAt(), lines);
    }

    private void requireValidRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidLeaveDateException("结束日期不能早于开始日期");
        }
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
