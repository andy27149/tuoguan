package com.tuoguan.backend.monthlyquickbill.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// month 格式 yyyy-MM；courseConsumptions 可为空（这个学生本月没报课外课，或没有消课记录）。
public record QuickBillSubmitRequest(@NotBlank String month, @Min(0) int mealDays, @Min(0) int leaveDays,
                                      @NotNull List<CourseConsumptionInput> courseConsumptions) {
}
