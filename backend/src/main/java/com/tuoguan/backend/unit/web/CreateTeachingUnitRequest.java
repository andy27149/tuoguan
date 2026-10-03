package com.tuoguan.backend.unit.web;

import com.tuoguan.backend.unit.domain.BillingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateTeachingUnitRequest(@NotNull BillingMode billingMode, @NotBlank String name,
                                         @NotNull Long teacherId, Integer lessonDurationMinutes,
                                         BigDecimal pricePerLesson) {
}
