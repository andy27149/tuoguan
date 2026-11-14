package com.tuoguan.backend.monthlyquickbill.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CourseConsumptionInput(@NotNull Long courseId, @Min(0) int count) {
}
