package com.tuoguan.backend.course.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SetMealRequest(@NotNull LocalDate date) {
}
