package com.tuoguan.backend.monthlyquickbill.service;

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
import java.util.stream.Collectors;

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
    private final StudentLeaveRecordService leaveRecordService;
    private final CourseConsumptionService courseConsumptionService;

    public MonthlyQuickBillService(StudentDao studentDao, TeachingUnitDao teachingUnitDao,
                                    StudentUnitEnrollmentDao enrollmentDao,
                                    StudentArrivalCheckinDao arrivalCheckinDao,
                                    StudentArrivalCheckinService arrivalCheckinService,
                                    StudentMealRecordService mealRecordService,
                                    StudentLeaveRecordService leaveRecordService,
                                    CourseConsumptionService courseConsumptionService) {
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.enrollmentDao = enrollmentDao;
        this.arrivalCheckinDao = arrivalCheckinDao;
        this.arrivalCheckinService = arrivalCheckinService;
        this.mealRecordService = mealRecordService;
        this.leaveRecordService = leaveRecordService;
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

        // 已有的真实到校记录不占用——这个月如果已经有人用看板打过卡，合成数据绕开这些日期走。
        Set<LocalDate> occupied = arrivalCheckinDao
                .findAllByStudentIdAndDateRange(studentId, month.atDay(1), month.atEndOfMonth()).stream()
                .map(StudentArrivalCheckin::checkinDate)
                .collect(Collectors.toCollection(HashSet::new));

        Long classTeacherId = classRoom.teacherId();

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
        // 课外课消课日期是独立的记录类型，不需要和用餐/请假日期互斥，整月工作日都能用；
        // 同一天如果已经有消课记录，recordBatchConsumption 会静默跳过，不会重复计费。
        List<LocalDate> courseDates = pickDates(month, input.count(), Set.of(),
                "课外课「" + course.name() + "」节数(" + input.count() + ")");
        for (LocalDate date : courseDates) {
            courseConsumptionService.recordBatchConsumption(course.teacherId(), course.id(), date,
                    List.of(studentId));
        }
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
