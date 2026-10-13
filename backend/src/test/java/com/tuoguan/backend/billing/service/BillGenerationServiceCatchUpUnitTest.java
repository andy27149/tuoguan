package com.tuoguan.backend.billing.service;

import com.tuoguan.backend.admin.web.CourseConsumptionSummaryRow;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.service.CourseAccountService;
import com.tuoguan.backend.course.web.ConsumptionCoverage;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 纯单元测试：只测 computeCourseConsumptionRows 的「补充值追溯抵扣」夹断逻辑
// （产品诊断 #54），不经过 generateBill 整条链路，所以其余 11 个构造参数（DAO）
// 全部传 null——computeCourseConsumptionRows 只用到 courseAccountService 和
// teachingUnitDao 这两个依赖。
@ExtendWith(MockitoExtension.class)
class BillGenerationServiceCatchUpUnitTest {

    @Mock
    private CourseAccountService courseAccountService;
    @Mock
    private TeachingUnitDao teachingUnitDao;

    private static final Long COURSE_ID = 10L;
    private static final Long STUDENT_ID = 1L;
    private static final YearMonth MONTH = YearMonth.of(2026, 1);

    private BillGenerationService newService() {
        return new BillGenerationService(null, courseAccountService, null, null, null, null, null, null, null, null,
                teachingUnitDao, null, null, null);
    }

    private ConsumptionCoverage uncoveredConsumption(long id, String date, BigDecimal price) {
        CourseConsumptionRecord record = new CourseConsumptionRecord(id, 1L, STUDENT_ID, COURSE_ID,
                LocalDate.parse(date), price, 2L, Instant.now());
        return new ConsumptionCoverage(record, false);
    }

    private void stubCourse() {
        when(teachingUnitDao.findById(COURSE_ID))
                .thenReturn(java.util.Optional.of(new TeachingUnit(COURSE_ID, 1L, 2L, "数学提高班",
                        BillingMode.LESSON_COUNT, 60, BigDecimal.TEN, true, Instant.now())));
    }

    @Test
    void netBalanceAlreadyBackToZeroSettlesTheWholeAmountByLaterRecharge() {
        stubCourse();
        when(courseAccountService.classifyConsumptions(STUDENT_ID)).thenReturn(List.of(
                uncoveredConsumption(1, "2026-01-05", BigDecimal.TEN), uncoveredConsumption(2, "2026-01-06", BigDecimal.TEN)));
        when(courseAccountService.getNetBalance(STUDENT_ID, COURSE_ID)).thenReturn(0);

        List<CourseConsumptionSummaryRow> rows = newService().computeCourseConsumptionRows(STUDENT_ID, MONTH, true);

        assertThat(rows).hasSize(1);
        CourseConsumptionSummaryRow row = rows.get(0);
        assertThat(row.lessonCount()).isEqualTo(2);
        assertThat(row.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.settledByRecharge()).isTrue();
    }

    @Test
    void netBalanceExactlyNegativeBillableSizeOwesEverythingAtTheBoundary() {
        stubCourse();
        when(courseAccountService.classifyConsumptions(STUDENT_ID)).thenReturn(List.of(
                uncoveredConsumption(1, "2026-01-05", BigDecimal.TEN), uncoveredConsumption(2, "2026-01-06", BigDecimal.TEN)));
        when(courseAccountService.getNetBalance(STUDENT_ID, COURSE_ID)).thenReturn(-2);

        List<CourseConsumptionSummaryRow> rows = newService().computeCourseConsumptionRows(STUDENT_ID, MONTH, true);

        CourseConsumptionSummaryRow row = rows.get(0);
        assertThat(row.lessonCount()).isEqualTo(2);
        assertThat(row.amount()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(row.settledByRecharge()).isFalse();
    }

    @Test
    void netBalanceMoreNegativeThanBillableSizeClampsWithoutOverflow() {
        stubCourse();
        when(courseAccountService.classifyConsumptions(STUDENT_ID)).thenReturn(List.of(
                uncoveredConsumption(1, "2026-01-05", BigDecimal.TEN), uncoveredConsumption(2, "2026-01-06", BigDecimal.TEN)));
        when(courseAccountService.getNetBalance(STUDENT_ID, COURSE_ID)).thenReturn(-7);

        List<CourseConsumptionSummaryRow> rows = newService().computeCourseConsumptionRows(STUDENT_ID, MONTH, true);

        CourseConsumptionSummaryRow row = rows.get(0);
        assertThat(row.lessonCount()).isEqualTo(2);
        assertThat(row.amount()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(row.settledByRecharge()).isFalse();
    }

    @Test
    void noBillableConsumptionProducesZeroAmountRowWithoutCallingGetNetBalance() {
        when(courseAccountService.classifyConsumptions(STUDENT_ID)).thenReturn(List.of());

        List<CourseConsumptionSummaryRow> rows = newService().computeCourseConsumptionRows(STUDENT_ID, MONTH, true);

        assertThat(rows).isEmpty();
    }
}
