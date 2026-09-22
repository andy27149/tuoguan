package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LeaveRangeRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, String reason) {
}
