package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record BatchRecordConsumptionRequest(@NotNull LocalDate date, @NotNull List<Long> presentStudentIds) {
}
