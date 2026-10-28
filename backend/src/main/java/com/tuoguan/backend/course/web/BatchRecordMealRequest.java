package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record BatchRecordMealRequest(@NotNull LocalDate date, @NotNull List<Long> presentStudentIds) {
}
