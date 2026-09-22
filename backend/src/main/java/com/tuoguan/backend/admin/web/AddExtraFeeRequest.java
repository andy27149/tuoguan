package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AddExtraFeeRequest(@NotBlank String name, @NotNull BigDecimal pricePerLesson) {
}
