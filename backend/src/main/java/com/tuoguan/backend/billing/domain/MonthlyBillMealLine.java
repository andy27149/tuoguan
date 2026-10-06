package com.tuoguan.backend.billing.domain;

import java.time.LocalDate;

public record MonthlyBillMealLine(Long id, Long monthlyBillId, LocalDate mealDate) {
}
