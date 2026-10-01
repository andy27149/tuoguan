package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCourseRequest(@NotBlank String name, @NotNull @Min(1) Integer lessonDurationMinutes) {
}
