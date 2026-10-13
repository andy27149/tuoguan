package com.tuoguan.backend.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MonthlyBillExtraFeeLine(Long id, Long monthlyBillId, String name, BigDecimal pricePerLesson,
                                       Integer lessonCount, BigDecimal amount, List<LocalDate> consumptionDates,
                                       boolean settledByRecharge) {
}
