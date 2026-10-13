package com.tuoguan.backend.course.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
import com.tuoguan.backend.course.web.ConsumptionCoverage;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 纯单元测试（不继承 IntegrationTestBase，不起 Spring/Testcontainers）：直接测
// CourseAccountService.classifyForCourse 这段余额回放算法本身，CourseAccountServiceTest
// 已经用真实数据库覆盖了"全覆盖/全不覆盖/部分覆盖"等场景，这里只补它没覆盖到的
// 零值/相等边界，避免重复。
@ExtendWith(MockitoExtension.class)
class CourseAccountServiceUnitTest {

    @Mock
    private StudentDao studentDao;
    @Mock
    private TeachingUnitDao teachingUnitDao;
    @Mock
    private TeacherDao teacherDao;
    @Mock
    private CourseRechargeRecordDao rechargeRecordDao;
    @Mock
    private CourseConsumptionRecordDao consumptionRecordDao;

    private final CourseAccountService service =
            new CourseAccountService(studentDao, teachingUnitDao, teacherDao, rechargeRecordDao, consumptionRecordDao);

    private static final Long COURSE_ID = 1L;

    private CourseRechargeRecord recharge(long id, int lessonCount, String date) {
        return new CourseRechargeRecord(id, 1L, 2L, COURSE_ID, lessonCount, null, 3L,
                LocalDate.parse(date).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant());
    }

    private CourseConsumptionRecord consumption(long id, String date) {
        return new CourseConsumptionRecord(id, 1L, 2L, COURSE_ID, LocalDate.parse(date), BigDecimal.TEN, 3L,
                Instant.now());
    }

    @Test
    void zeroRechargeMeansEveryConsumptionIsUncovered() {
        List<ConsumptionCoverage> result = service.classifyForCourse(List.of(),
                List.of(consumption(1, "2026-01-05"), consumption(2, "2026-01-06")));

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> !c.coveredByBalance());
    }

    @Test
    void zeroConsumptionProducesEmptyResult() {
        List<ConsumptionCoverage> result = service.classifyForCourse(List.of(recharge(1, 5, "2026-01-01")),
                List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void rechargeCountExactlyEqualsConsumptionCountCoversEverythingWithZeroRemainingBalance() {
        List<ConsumptionCoverage> result = service.classifyForCourse(
                List.of(recharge(1, 3, "2026-01-01")),
                List.of(consumption(1, "2026-01-02"), consumption(2, "2026-01-03"), consumption(3, "2026-01-04")));

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(ConsumptionCoverage::coveredByBalance);
    }

    @Test
    void consumptionExceedingRechargeByOneLeavesOnlyTheLastOneUncovered() {
        List<ConsumptionCoverage> result = service.classifyForCourse(
                List.of(recharge(1, 2, "2026-01-01")),
                List.of(consumption(1, "2026-01-02"), consumption(2, "2026-01-03"), consumption(3, "2026-01-04")));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).coveredByBalance()).isTrue();
        assertThat(result.get(1).coveredByBalance()).isTrue();
        assertThat(result.get(2).coveredByBalance()).isFalse();
    }
}
