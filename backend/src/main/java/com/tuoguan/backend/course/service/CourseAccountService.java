package com.tuoguan.backend.course.service;

import com.tuoguan.backend.admin.web.LowBalanceRow;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.course.web.ConsumptionCoverage;
import com.tuoguan.backend.course.web.ConsumptionRecordResponse;
import com.tuoguan.backend.course.web.CourseActivityRow;
import com.tuoguan.backend.course.web.CourseBalanceRow;
import com.tuoguan.backend.course.web.RechargeRecordResponse;
import com.tuoguan.backend.course.web.StudentCourseStatement;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CourseAccountService {

    // 数据库连接以此时区解读 DATETIME 列（见 application.yml 的 JDBC URL serverTimezone
    // 参数），充值记录只有 Instant 时间戳，要和消课记录的 LocalDate 按"自然日"对齐比较时
    // 必须用同一个时区转换，不能依赖 JVM 默认时区（本地开发机和生产容器可能不一致）。
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;
    private final TeacherDao teacherDao;
    private final CourseRechargeRecordDao rechargeRecordDao;
    private final CourseConsumptionRecordDao consumptionRecordDao;

    public CourseAccountService(StudentDao studentDao, TeachingUnitDao teachingUnitDao, TeacherDao teacherDao,
                                 CourseRechargeRecordDao rechargeRecordDao,
                                 CourseConsumptionRecordDao consumptionRecordDao) {
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
        this.teacherDao = teacherDao;
        this.rechargeRecordDao = rechargeRecordDao;
        this.consumptionRecordDao = consumptionRecordDao;
    }

    public CourseRechargeRecord recharge(Long institutionId, Long studentId, Long recordedByTeacherId,
                                          Long courseId, Integer lessonCount, String note) {
        // 托管班学生（同时报了课外课）现在也允许预充值——覆盖判定
        // （classifyConsumptions）本来就是按 (学生, 课程) 维度通用处理的，不区分学生
        // 身份，开放充值不需要改计费逻辑本身。
        requireStudentInInstitution(institutionId, studentId);
        requireCourseInInstitution(institutionId, courseId);
        Long id = rechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId,
                lessonCount, note, recordedByTeacherId, null));
        return rechargeRecordDao.findAllByStudentId(studentId).stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Recharge record not found: " + id));
    }

    public StudentCourseStatement getStatement(Long institutionId, Long studentId) {
        requireStudentInInstitution(institutionId, studentId);
        List<CourseRechargeRecord> recharges = rechargeRecordDao.findAllByStudentId(studentId);
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao.findAllByStudentId(studentId);

        Map<Long, String> courseNames = new HashMap<>();
        Map<Long, String> teacherNames = new HashMap<>();

        Set<Long> courseIds = new LinkedHashSet<>();
        recharges.forEach(r -> courseIds.add(r.teachingUnitId()));
        consumptions.forEach(c -> courseIds.add(c.teachingUnitId()));

        List<CourseBalanceRow> balances = new ArrayList<>();
        for (Long courseId : courseIds) {
            String courseName = resolveCourseName(courseNames, courseId);
            int lessonsRecharged = recharges.stream()
                    .filter(r -> r.teachingUnitId().equals(courseId))
                    .mapToInt(CourseRechargeRecord::lessonCount)
                    .sum();
            int lessonsConsumed = (int) consumptions.stream()
                    .filter(c -> c.teachingUnitId().equals(courseId))
                    .count();
            balances.add(new CourseBalanceRow(courseId, courseName, lessonsRecharged, lessonsConsumed,
                    lessonsRecharged - lessonsConsumed));
        }

        List<RechargeRecordResponse> rechargeResponses = recharges.stream()
                .map(r -> RechargeRecordResponse.from(r, resolveCourseName(courseNames, r.teachingUnitId())))
                .toList();
        List<ConsumptionRecordResponse> consumptionResponses = consumptions.stream()
                .map(c -> ConsumptionRecordResponse.from(c, resolveCourseName(courseNames, c.teachingUnitId()),
                        resolveTeacherName(teacherNames, c.recordedByTeacherId())))
                .toList();

        return new StudentCourseStatement(balances, rechargeResponses, consumptionResponses);
    }

    public List<LowBalanceRow> getLowBalanceEntries(Long institutionId, int threshold) {
        List<CourseRechargeRecord> recharges = rechargeRecordDao.findAllByInstitutionId(institutionId);
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao.findAllByInstitutionId(institutionId);

        record StudentCourseKey(Long studentId, Long teachingUnitId) {
        }

        Set<StudentCourseKey> keys = new LinkedHashSet<>();
        recharges.forEach(r -> keys.add(new StudentCourseKey(r.studentId(), r.teachingUnitId())));
        consumptions.forEach(c -> keys.add(new StudentCourseKey(c.studentId(), c.teachingUnitId())));

        Map<Long, String> studentNames = new HashMap<>();
        Map<Long, String> courseNames = new HashMap<>();

        List<LowBalanceRow> rows = new ArrayList<>();
        for (StudentCourseKey key : keys) {
            int recharged = recharges.stream()
                    .filter(r -> r.studentId().equals(key.studentId()) && r.teachingUnitId().equals(key.teachingUnitId()))
                    .mapToInt(CourseRechargeRecord::lessonCount)
                    .sum();
            int consumed = (int) consumptions.stream()
                    .filter(c -> c.studentId().equals(key.studentId()) && c.teachingUnitId().equals(key.teachingUnitId()))
                    .count();
            int balance = recharged - consumed;
            if (balance >= threshold) {
                continue;
            }
            String studentName = studentNames.computeIfAbsent(key.studentId(),
                    id -> studentDao.findById(id).map(Student::name).orElse("-"));
            String courseName = courseNames.computeIfAbsent(key.teachingUnitId(),
                    id -> teachingUnitDao.findById(id).map(TeachingUnit::name).orElse("-"));
            rows.add(new LowBalanceRow(key.studentId(), studentName, key.teachingUnitId(), courseName, balance));
        }

        return rows.stream()
                .sorted(Comparator.comparingInt(LowBalanceRow::balance))
                .toList();
    }

    public int countConsumptionsInMonth(Long institutionId, YearMonth month) {
        return (int) consumptionRecordDao.findAllByInstitutionId(institutionId).stream()
                .filter(c -> YearMonth.from(c.consumptionDate()).equals(month))
                .count();
    }

    // 供托管班学生（同时报名课外课）的家长分享页使用：只给课程名+最近消课日期，不带
    // 充值/余额——这类学生的课外课费用走托管月度账单附加费，从不预充值，余额概念不成立。
    public List<CourseActivityRow> getCourseActivity(Long institutionId, Long studentId) {
        requireStudentInInstitution(institutionId, studentId);
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao.findAllByStudentId(studentId);

        Map<Long, List<LocalDate>> datesByCourse = new LinkedHashMap<>();
        for (CourseConsumptionRecord record : consumptions) {
            datesByCourse.computeIfAbsent(record.teachingUnitId(), id -> new ArrayList<>())
                    .add(record.consumptionDate());
        }

        Map<Long, String> courseNames = new HashMap<>();
        List<CourseActivityRow> rows = new ArrayList<>();
        for (Map.Entry<Long, List<LocalDate>> entry : datesByCourse.entrySet()) {
            List<LocalDate> recentDates = entry.getValue().stream()
                    .sorted(Comparator.reverseOrder())
                    .limit(5)
                    .toList();
            rows.add(new CourseActivityRow(entry.getKey(), resolveCourseName(courseNames, entry.getKey()),
                    recentDates));
        }
        return rows;
    }

    // 核心覆盖判定（产品诊断 #02 统一方案）：按"学生+课程"二元组，把充值和消课事件按
    // 自然日从余额 0 开始回放一遍——充值加课时数，消课先看当前余额是否 >0 再判定是否被
    // 覆盖，不论是否覆盖余额都照常减 1（和现有 CourseBalanceRow 的 recharged-consumed
    // 公式完全一致，这里只是多记录每一条消课当时是否被覆盖）。不加 schema、不存状态位，
    // 账单生成时只对"未覆盖"的记录收费；有托管班的学生从不充值，天然恒为全部未覆盖。
    public List<ConsumptionCoverage> classifyConsumptions(Long studentId) {
        List<CourseRechargeRecord> recharges = rechargeRecordDao.findAllByStudentId(studentId);
        List<CourseConsumptionRecord> consumptions = consumptionRecordDao.findAllByStudentId(studentId);

        Map<Long, List<CourseRechargeRecord>> rechargesByCourse = recharges.stream()
                .collect(Collectors.groupingBy(CourseRechargeRecord::teachingUnitId));
        Map<Long, List<CourseConsumptionRecord>> consumptionsByCourse = consumptions.stream()
                .collect(Collectors.groupingBy(CourseConsumptionRecord::teachingUnitId));

        Set<Long> courseIds = new LinkedHashSet<>();
        courseIds.addAll(rechargesByCourse.keySet());
        courseIds.addAll(consumptionsByCourse.keySet());

        List<ConsumptionCoverage> result = new ArrayList<>();
        for (Long courseId : courseIds) {
            result.addAll(classifyForCourse(rechargesByCourse.getOrDefault(courseId, List.of()),
                    consumptionsByCourse.getOrDefault(courseId, List.of())));
        }
        return result;
    }

    private record Event(LocalDate date, int order, long id, int rechargeDelta,
                          CourseConsumptionRecord consumption) {
    }

    private List<ConsumptionCoverage> classifyForCourse(List<CourseRechargeRecord> recharges,
                                                          List<CourseConsumptionRecord> consumptions) {
        List<Event> events = new ArrayList<>();
        for (CourseRechargeRecord r : recharges) {
            // order=0：同一天的充值在排序上先于消课处理，避免"同日充值能不能覆盖同日消课"
            // 产生歧义。
            events.add(new Event(r.createdAt().atZone(ZONE).toLocalDate(), 0, r.id(), r.lessonCount(), null));
        }
        for (CourseConsumptionRecord c : consumptions) {
            events.add(new Event(c.consumptionDate(), 1, c.id(), 0, c));
        }
        events.sort(Comparator.comparing(Event::date).thenComparingInt(Event::order).thenComparingLong(Event::id));

        List<ConsumptionCoverage> coverage = new ArrayList<>();
        int balance = 0;
        for (Event event : events) {
            if (event.consumption() == null) {
                balance += event.rechargeDelta();
            } else {
                boolean covered = balance > 0;
                balance -= 1;
                coverage.add(new ConsumptionCoverage(event.consumption(), covered));
            }
        }
        return coverage;
    }

    private String resolveCourseName(Map<Long, String> cache, Long courseId) {
        return cache.computeIfAbsent(courseId,
                id -> teachingUnitDao.findById(id).map(TeachingUnit::name).orElse(null));
    }

    private String resolveTeacherName(Map<Long, String> cache, Long teacherId) {
        return cache.computeIfAbsent(teacherId,
                id -> teacherDao.findById(id).map(Teacher::name).orElse(null));
    }

    private Student requireStudentInInstitution(Long institutionId, Long studentId) {
        Student student = studentDao.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found: " + studentId));
        if (!student.institutionId().equals(institutionId)) {
            throw new NotFoundException("Student not found: " + studentId);
        }
        return student;
    }

    private TeachingUnit requireCourseInInstitution(Long institutionId, Long courseId) {
        TeachingUnit course = teachingUnitDao.findById(courseId)
                .filter(c -> c.billingMode() == BillingMode.LESSON_COUNT)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        if (!course.institutionId().equals(institutionId)) {
            throw new NotFoundException("Course not found: " + courseId);
        }
        return course;
    }
}
