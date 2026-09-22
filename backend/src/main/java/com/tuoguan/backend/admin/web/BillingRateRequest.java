package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record BillingRateRequest(@NotNull BigDecimal tuitionRatePerMonth, @NotNull BigDecimal mealRatePerDay) {
}
