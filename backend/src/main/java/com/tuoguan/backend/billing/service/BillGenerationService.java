package com.tuoguan.backend.billing.service;

import com.tuoguan.backend.admin.web.BillOverviewRow;
import com.tuoguan.backend.admin.web.BillingRateNotConfiguredException;
import com.tuoguan.backend.admin.web.ClassBillingRateRow;
import com.tuoguan.backend.admin.web.DuplicateExtraFeeException;
import com.tuoguan.backend.admin.web.InvalidLeaveDateException;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.billing.dao.MonthlyBillDao;
import com.tuoguan.backend.billing.dao.MonthlyBillExtraFeeLineDao;
import com.tuoguan.backend.billing.dao.StudentExtraFeeDao;
import com.tuoguan.backend.billing.dao.StudentExtraFeeLessonCountDao;
import com.tuoguan.backend.billing.dao.StudentLeaveRecordDao;
import com.tuoguan.backend.billing.domain.ClassBillingRate;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.domain.StudentExtraFee;
import com.tuoguan.backend.admin.web.StudentExtraFeeRow;
import com.tuoguan.backend.billing.domain.StudentLeaveRecord;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
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
    private final StudentExtraFeeDao studentExtraFeeDao;
    private final StudentExtraFeeLessonCountDao studentExtraFeeLessonCountDao;
    private final StudentLeaveRecordDao studentLeaveRecordDao;
    private final MonthlyBillDao monthlyBillDao;
    private final MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao;
    private final StudentDao studentDao;
    private final ClassRoomDao classRoomDao;
    private final TeacherDao teacherDao;

    public BillGenerationService(ClassBillingRateDao classBillingRateDao, StudentExtraFeeDao studentExtraFeeDao,
                                  StudentExtraFeeLessonCountDao studentExtraFeeLessonCountDao,
                                  StudentLeaveRecordDao studentLeaveRecordDao, MonthlyBillDao monthlyBillDao,
                                  MonthlyBillExtraFeeLineDao monthlyBillExtraFeeLineDao, StudentDao studentDao,
                                  ClassRoomDao classRoomDao, TeacherDao teacherDao) {
        this.classBillingRateDao = classBillingRateDao;
        this.studentExtraFeeDao = studentExtraFeeDao;
        this.studentExtraFeeLessonCountDao = studentExtraFeeLessonCountDao;
        this.studentLeaveRecordDao = studentLeaveRecordDao;
        this.monthlyBillDao = monthlyBillDao;
        this.monthlyBillExtraFeeLineDao = monthlyBillExtraFeeLineDao;
        this.studentDao = studentDao;
        this.classRoomDao = classRoomDao;
        this.teacherDao = teacherDao;
    }

    public Optional<ClassBillingRate> getBillingRate(Long institutionId, Long classRoomId) {
        requireClassRoomInInstitution(institutionId, classRoomId);
        return classBillingRateDao.findByClassRoomId(classRoomId);
    }

    public ClassBillingRate upsertBillingRate(Long institutionId, Long classRoomId, BigDecimal tuitionRatePerMonth,
                                               BigDecimal mealRatePerDay) {
        requireClassRoomInInstitution(institutionId, classRoomId);
        classBillingRateDao.upsert(institutionId, classRoomId, tuitionRatePerMonth, mealRatePerDay);
        return classBillingRateDao.findByClassRoomId(classRoomId)
                .orElseThrow(() -> new IllegalStateException("Billing rate not found after upsert: " + classRoomId));
    }

    public List<ClassBillingRateRow> listAllClassBillingRates(Long institutionId) {
        return classRoomDao.findAllByInstitutionId(institutionId).stream()
                .map(classRoom -> {
                    ClassBillingRate rate = classBillingRateDao.findByClassRoomId(classRoom.id()).orElse(null);
                    return new ClassBillingRateRow(classRoom.id(), classRoom.name(),
                            rate != null ? rate.tuitionRatePerMonth() : null,
                            rate != null ? rate.mealRatePerDay() : null);
                })
                .toList();
    }

    @Transactional
    public void bulkSetBillingRate(Long institutionId, BigDecimal tuitionRatePerMonth, BigDecimal mealRatePerDay) {
        for (ClassRoom classRoom : classRoomDao.findAllByInstitutionId(institutionId)) {
            classBillingRateDao.upsert(institutionId, classRoom.id(), tuitionRatePerMonth, mealRatePerDay);
        }
    }

    public List<StudentExtraFeeRow> listExtraFeesForMonth(Long institutionId, Long studentId, YearMonth month) {
        requireStudentInInstitution(institutionId, studentId);
        return studentExtraFeeDao.findAllByStudentId(studentId).stream()
                .map(fee -> toRow(fee, month))
                .toList();
    }

    public StudentExtraFee addExtraFee(Long institutionId, Long studentId, String name, BigDecimal pricePerLesson) {
        requireStudentInInstitution(institutionId, studentId);
        boolean duplicate = studentExtraFeeDao.findAllByStudentId(studentId).stream()
                .anyMatch(f -> f.name().equals(name));
        if (duplicate) {
            throw new DuplicateExtraFeeException("Extra fee already exists: " + name);
        }
        Long id = studentExtraFeeDao.insert(institutionId, studentId, name, pricePerLesson);
        return studentExtraFeeDao.findById(id)
                .orElseThrow(() -> new IllegalStateException("Extra fee not found after insert: " + id));
    }

    public void deleteExtraFee(Long institutionId, Long studentId, Long feeId) {
        requireStudentInInstitution(institutionId, studentId);
        StudentExtraFee fee = studentExtraFeeDao.findById(feeId)
                .orElseThrow(() -> new NotFoundException("Extra fee not found: " + feeId));
        if (!fee.studentId().equals(studentId)) {
            throw new NotFoundException("Extra fee not found: " + feeId);
        }
        studentExtraFeeDao.deleteById(feeId);
    }

    public StudentExtraFeeRow setExtraFeeLessonCount(Long institutionId, Long studentId, Long feeId, YearMonth month,
                                                       int lessonCount) {
        requireStudentInInstitution(institutionId, studentId);
        StudentExtraFee fee = studentExtraFeeDao.findById(feeId)
                .orElseThrow(() -> new NotFoundException("Extra fee not found: " + feeId));
        if (!fee.studentId().equals(studentId)) {
            throw new NotFoundException("Extra fee not found: " + feeId);
        }
        studentExtraFeeLessonCountDao.upsert(institutionId, feeId, month.toString(), lessonCount);
        return toRow(fee, month);
    }

    private StudentExtraFeeRow toRow(StudentExtraFee fee, YearMonth month) {
        int lessonCount = studentExtraFeeLessonCountDao.findLessonCount(fee.id(), month.toString()).orElse(0);
        BigDecimal amount = fee.pricePerLesson().multiply(BigDecimal.valueOf(lessonCount));
        return new StudentExtraFeeRow(fee.id(), fee.name(), fee.pricePerLesson(), lessonCount, amount);
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
            studentLeaveRecordDao.upsert(institutionId, studentId, student.classRoomId(), d, reason);
        }
        return studentLeaveRecordDao.findAllByStudentIdAndDateRange(studentId, startDate, endDate);
    }

    public void cancelLeave(Long institutionId, Long studentId, LocalDate date) {
        requireStudentInInstitution(institutionId, studentId);
        studentLeaveRecordDao.deleteByStudentIdAndDate(studentId, date);
    }

    public List<MonthlyBill> listClassBills(Long institutionId, Long classRoomId, YearMonth month) {
        requireClassRoomInInstitution(institutionId, classRoomId);
        return monthlyBillDao.findAllByClassRoomIdAndYearMonth(classRoomId, month).stream()
                .map(this::enrich)
                .toList();
    }

    public MonthlyBill generateBill(Long institutionId, Long studentId, YearMonth month) {
        return generateBill(institutionId, studentId, month, null);
    }

    @Transactional
    public MonthlyBill generateBill(Long institutionId, Long studentId, YearMonth month, BigDecimal tuitionOverride) {
        Student student = requireStudentInInstitution(institutionId, studentId);
        ClassBillingRate rate = classBillingRateDao.findByClassRoomId(student.classRoomId())
                .orElseThrow(() -> new BillingRateNotConfiguredException("班级未配置计费单价"));

        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        int totalWeekdays = countWeekdays(start, end);
        int leaveDays = studentLeaveRecordDao.countByStudentIdAndDateRange(studentId, start, end);
        int attendanceDays = Math.max(0, totalWeekdays - leaveDays);

        BigDecimal tuitionAmount = tuitionOverride != null ? tuitionOverride : rate.tuitionRatePerMonth();
        BigDecimal mealAmount = rate.mealRatePerDay().multiply(BigDecimal.valueOf(attendanceDays));
        List<StudentExtraFeeRow> extraFeeRows = studentExtraFeeDao.findAllByStudentId(studentId).stream()
                .map(fee -> toRow(fee, month))
                .toList();
        BigDecimal extraFeeTotal = extraFeeRows.stream().map(StudentExtraFeeRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = tuitionAmount.add(mealAmount).add(extraFeeTotal);

        Long billId = monthlyBillDao.upsert(institutionId, studentId, student.classRoomId(), month, totalWeekdays,
                leaveDays, attendanceDays, tuitionAmount, mealAmount, extraFeeTotal, totalAmount);
        monthlyBillExtraFeeLineDao.deleteAllByBillId(billId);
        extraFeeRows.forEach(row -> monthlyBillExtraFeeLineDao.insert(billId, row.name(), row.pricePerLesson(),
                row.lessonCount(), row.amount()));

        return monthlyBillDao.findById(billId)
                .map(this::enrich)
                .orElseThrow(() -> new IllegalStateException("Bill not found after upsert: " + billId));
    }

    public List<MonthlyBill> generateBillsForClass(Long institutionId, Long classRoomId, YearMonth month) {
        ClassRoom classRoom = requireClassRoomInInstitution(institutionId, classRoomId);
        return studentDao.findAllByClassRoomId(classRoom.id()).stream()
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
        List<ClassRoom> classRooms = classRoomId != null
                ? List.of(requireClassRoomInInstitution(institutionId, classRoomId))
                : classRoomDao.findAllByInstitutionId(institutionId);

        List<BillOverviewRow> rows = new ArrayList<>();
        for (ClassRoom classRoom : classRooms) {
            String teacherName = teacherDao.findById(classRoom.teacherId()).map(Teacher::name).orElse("-");
            Map<Long, MonthlyBill> billsByStudentId = monthlyBillDao
                    .findAllByClassRoomIdAndYearMonth(classRoom.id(), month).stream()
                    .collect(Collectors.toMap(MonthlyBill::studentId, b -> b));
            for (Student student : studentDao.findAllByClassRoomId(classRoom.id())) {
                if (!student.enrolled()) {
                    continue;
                }
                if (studentName != null && !studentName.isBlank() && !student.name().contains(studentName)) {
                    continue;
                }
                MonthlyBill bill = billsByStudentId.get(student.id());
                rows.add(new BillOverviewRow(student.id(), student.name(), classRoom.id(), classRoom.name(),
                        teacherName, bill != null ? bill.id() : null, bill != null ? bill.totalAmount() : null,
                        bill != null && bill.isPaid(), month.toString()));
            }
        }
        return rows;
    }

    public List<BillOverviewRow> getBillOverviewAllMonths(Long institutionId, Long classRoomId, String studentName) {
        List<ClassRoom> classRooms = classRoomId != null
                ? List.of(requireClassRoomInInstitution(institutionId, classRoomId))
                : classRoomDao.findAllByInstitutionId(institutionId);

        List<BillOverviewRow> rows = new ArrayList<>();
        for (ClassRoom classRoom : classRooms) {
            String teacherName = teacherDao.findById(classRoom.teacherId()).map(Teacher::name).orElse("-");
            Map<Long, List<MonthlyBill>> billsByStudentId = monthlyBillDao.findAllByClassRoomId(classRoom.id())
                    .stream().collect(Collectors.groupingBy(MonthlyBill::studentId));
            for (Student student : studentDao.findAllByClassRoomId(classRoom.id())) {
                if (!student.enrolled()) {
                    continue;
                }
                if (studentName != null && !studentName.isBlank() && !student.name().contains(studentName)) {
                    continue;
                }
                List<MonthlyBill> bills = billsByStudentId.get(student.id());
                if (bills == null || bills.isEmpty()) {
                    rows.add(new BillOverviewRow(student.id(), student.name(), classRoom.id(), classRoom.name(),
                            teacherName, null, null, false, "-"));
                } else {
                    for (MonthlyBill bill : bills) {
                        rows.add(new BillOverviewRow(student.id(), student.name(), classRoom.id(), classRoom.name(),
                                teacherName, bill.id(), bill.totalAmount(), bill.isPaid(), bill.yearMonth().toString()));
                    }
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
        return new MonthlyBill(bill.id(), bill.institutionId(), bill.studentId(), bill.classRoomId(),
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

    private ClassRoom requireClassRoomInInstitution(Long institutionId, Long classRoomId) {
        ClassRoom classRoom = classRoomDao.findById(classRoomId)
                .orElseThrow(() -> new NotFoundException("ClassRoom not found: " + classRoomId));
        if (!classRoom.institutionId().equals(institutionId)) {
            throw new NotFoundException("ClassRoom not found: " + classRoomId);
        }
        return classRoom;
    }
}
