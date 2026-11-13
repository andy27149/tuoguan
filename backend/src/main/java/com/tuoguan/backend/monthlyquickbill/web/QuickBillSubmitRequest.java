package com.tuoguan.backend.monthlyquickbill.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// month 格式 yyyy-MM；courseConsumptions 可为空（这个学生本月没报课外课，或没有消课记录）。
// 列表本身加 @NotNull 还不够——没有 @Valid，Bean Validation 不会级联校验列表里每个
// CourseConsumptionInput 自己的 @NotNull/@Min 约束，负数 count 或空 courseId 会直接
// 流进 service 层。
public record QuickBillSubmitRequest(@NotBlank String month, @Min(0) int mealDays, @Min(0) int leaveDays,
                                      @NotNull @Valid List<CourseConsumptionInput> courseConsumptions) {
}
