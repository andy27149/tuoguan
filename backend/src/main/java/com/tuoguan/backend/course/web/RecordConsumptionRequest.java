package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordConsumptionRequest(@NotNull Long studentId, @NotNull LocalDate date, boolean confirm) {
}
