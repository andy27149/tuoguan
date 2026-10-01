package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RechargeRequest(@NotNull Long courseId, @NotNull @Min(1) Integer lessonCount, String note) {
}
