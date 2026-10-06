package com.tuoguan.backend.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record MonthlyBill(Long id, Long institutionId, Long studentId, Long teachingUnitId, YearMonth yearMonth,
                           int totalWeekdays, int leaveDays, int attendanceDays, BigDecimal tuitionAmount,
                           BigDecimal mealAmount, BigDecimal extraFeeTotal, BigDecimal totalAmount, boolean isPaid,
                           Instant generatedAt, List<MonthlyBillExtraFeeLine> extraFeeLines,
                           List<LocalDate> mealRecordDates) {
}
