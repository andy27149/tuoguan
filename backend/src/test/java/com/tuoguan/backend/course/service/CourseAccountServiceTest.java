package com.tuoguan.backend.course.service;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.course.web.ConsumptionCoverage;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CourseAccountServiceTest extends IntegrationTestBase {

    @Autowired
    private CourseAccountService courseAccountService;

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private CourseRechargeRecordDao rechargeRecordDao;

    @Autowired
    private CourseConsumptionRecordDao consumptionRecordDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 消课的 consumptionDate 完全由调用方指定，而充值的 createdAt 由数据库 DEFAULT
    // CURRENT_TIMESTAMP 在插入时自动生成（DAO 不接受外部传入）——测试里没法精确控制充值
    // 发生的具体时刻，只能控制消课发生在"今天"（充值必然发生的那一天）之前还是之后，
    // 用足够远的过去/未来日期给消课记录来摆出需要的相对顺序。
    private static final LocalDate FAR_PAST = LocalDate.of(2020, 1, 1);
    private static final LocalDate FAR_FUTURE = LocalDate.of(2099, 1, 1);

    private long insertInstitution(String name) {
        return institutionDao.insert(name);
    }

    private long insertTeacher(long institutionId, String phone) {
        return teacherDao.insert(new Teacher(null, institutionId, phone,
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
    }

    private long insertCourse(long institutionId, long teacherId, String name) {
        return teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, name,
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
    }

    private long insertClassRoom(long institutionId, long teacherId, String name) {
        return teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, name,
                BillingMode.MONTHLY, null, null, true, null));
    }

    private long insertOffCampusStudent(long institutionId, String name) {
        return studentDao.insert(new Student(null, institutionId, null, name, null, true, null, null));
    }

    private void recharge(long institutionId, long studentId, long courseId, long teacherId, int lessonCount) {
        rechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId, lessonCount,
                null, teacherId, null));
    }

    private long consume(long institutionId, long studentId, long courseId, long teacherId, LocalDate date) {
        return consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                date, new BigDecimal("50.00"), teacherId, null));
    }

    private Map<Long, Boolean> coverageById(List<ConsumptionCoverage> coverages) {
        return coverages.stream()
                .collect(Collectors.toMap(c -> c.record().id(), ConsumptionCoverage::coveredByBalance));
    }

    @Test
    void allConsumptionCoveredWhenRechargedUpfront() {
        long institutionId = insertInstitution("账户测试机构A");
        long teacherId = insertTeacher(institutionId, "13900040001");
        long courseId = insertCourse(institutionId, teacherId, "围棋课");
        long studentId = insertOffCampusStudent(institutionId, "小外");

        recharge(institutionId, studentId, courseId, teacherId, 10);
        long c1 = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE);
        long c2 = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE.plusDays(1));

        Map<Long, Boolean> coverage = coverageById(courseAccountService.classifyConsumptions(studentId));
        assertThat(coverage.get(c1)).isTrue();
        assertThat(coverage.get(c2)).isTrue();
    }

    @Test
    void allConsumptionUncoveredWhenNeverRecharged() {
        long institutionId = insertInstitution("账户测试机构B");
        long teacherId = insertTeacher(institutionId, "13900040002");
        long courseId = insertCourse(institutionId, teacherId, "围棋课");
        long studentId = insertOffCampusStudent(institutionId, "小外");

        long c1 = consume(institutionId, studentId, courseId, teacherId, FAR_PAST);
        long c2 = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE);

        Map<Long, Boolean> coverage = coverageById(courseAccountService.classifyConsumptions(studentId));
        assertThat(coverage.get(c1)).isFalse();
        assertThat(coverage.get(c2)).isFalse();
    }

    @Test
    void partialCoverageOnlyCoversEarliestConsumptionsUpToBalance() {
        long institutionId = insertInstitution("账户测试机构C");
        long teacherId = insertTeacher(institutionId, "13900040003");
        long courseId = insertCourse(institutionId, teacherId, "围棋课");
        long studentId = insertOffCampusStudent(institutionId, "小外");

        recharge(institutionId, studentId, courseId, teacherId, 2);
        // 三次消课都在充值之后（今天）发生，插入顺序即为按 id 排序的处理顺序。
        long c1 = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE);
        long c2 = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE);
        long c3 = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE);

        Map<Long, Boolean> coverage = coverageById(courseAccountService.classifyConsumptions(studentId));
        assertThat(coverage.get(c1)).isTrue();
        assertThat(coverage.get(c2)).isTrue();
        assertThat(coverage.get(c3)).isFalse();
    }

    @Test
    void midPeriodRechargeCoversOnlyLaterConsumptionNotEarlierOne() {
        long institutionId = insertInstitution("账户测试机构D");
        long teacherId = insertTeacher(institutionId, "13900040004");
        long courseId = insertCourse(institutionId, teacherId, "围棋课");
        long studentId = insertOffCampusStudent(institutionId, "小外");

        // before 先于充值发生时余额为 0，判定未覆盖，并且照常把余额扣到 -1（呼应"余额不论
        // 是否覆盖都照常减 1"）；充值 2 课时才能先填平这笔历史欠账、再余出 1 课时覆盖 after。
        // 这里刻意验证"充值不会倒追回头覆盖已经判定未覆盖的历史消课"。
        long before = consume(institutionId, studentId, courseId, teacherId, FAR_PAST);
        recharge(institutionId, studentId, courseId, teacherId, 2);
        long after = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE);

        Map<Long, Boolean> coverage = coverageById(courseAccountService.classifyConsumptions(studentId));
        assertThat(coverage.get(before)).isFalse();
        assertThat(coverage.get(after)).isTrue();
    }

    @Test
    void coverageIsIndependentPerCourse() {
        long institutionId = insertInstitution("账户测试机构E");
        long teacherId = insertTeacher(institutionId, "13900040005");
        long courseA = insertCourse(institutionId, teacherId, "围棋课");
        long courseB = insertCourse(institutionId, teacherId, "书法课");
        long studentId = insertOffCampusStudent(institutionId, "小外");

        recharge(institutionId, studentId, courseA, teacherId, 5);
        // courseB 从未充值。
        long coveredInA = consume(institutionId, studentId, courseA, teacherId, FAR_FUTURE);
        long uncoveredInB = consume(institutionId, studentId, courseB, teacherId, FAR_FUTURE);

        Map<Long, Boolean> coverage = coverageById(courseAccountService.classifyConsumptions(studentId));
        assertThat(coverage.get(coveredInA)).isTrue();
        assertThat(coverage.get(uncoveredInB)).isFalse();
    }

    @Test
    void classRoomStudentConsumptionIsAlwaysUncoveredSinceTheyCanNeverRecharge() {
        long institutionId = insertInstitution("账户测试机构F");
        long teacherId = insertTeacher(institutionId, "13900040006");
        long courseId = insertCourse(institutionId, teacherId, "围棋课");
        long classRoomId = insertClassRoom(institutionId, teacherId, "一年级1班");
        long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小双", "一年级1班",
                true, null, null));

        long c1 = consume(institutionId, studentId, courseId, teacherId, FAR_PAST);
        long c2 = consume(institutionId, studentId, courseId, teacherId, FAR_FUTURE);

        Map<Long, Boolean> coverage = coverageById(courseAccountService.classifyConsumptions(studentId));
        assertThat(coverage.get(c1)).isFalse();
        assertThat(coverage.get(c2)).isFalse();
    }
}
