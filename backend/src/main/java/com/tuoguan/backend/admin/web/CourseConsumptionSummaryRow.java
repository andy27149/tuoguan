package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

/**
 * {@code lessonCount}/{@code amount} 只统计「未被预充值覆盖、需要计入账单」的消课
 * （产品诊断 #02 统一方案）；{@code coveredByBalanceCount} 是同一个月里已经被预充值
 * 余额覆盖、不需要再收费的消课次数，仅用于「费用管理」弹窗向管理员展示完整拆分，
 * 不计入 {@code amount}。
 */
public record CourseConsumptionSummaryRow(Long courseId, String courseName, BigDecimal pricePerLesson,
                                           int lessonCount, BigDecimal amount, int coveredByBalanceCount) {
}
