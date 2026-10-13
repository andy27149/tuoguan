package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code lessonCount}/{@code amount} 只统计「未被预充值覆盖、需要计入账单」的消课
 * （产品诊断 #02 统一方案）；{@code coveredByBalanceCount} 是同一个月里已经被预充值
 * 余额覆盖、不需要再收费的消课次数，仅用于「费用管理」弹窗向管理员展示完整拆分，
 * 不计入 {@code amount}。{@code billableDates} 是全部「未被充值覆盖」的消课具体发生
 * 日期（产品反馈 #51），升序排列，固定有 {@code lessonCount} 条，不受
 * {@code settledByRecharge} 影响。{@code settledByRecharge}（产品诊断 #54）：这些
 * 消课发生时没有被充值覆盖，但生成账单这一刻，这门课「全部历史净余额」（充值合计-消课
 * 合计）已经不欠或欠得更少了——说明账单生成前学生又补了充值，{@code amount} 会相应
 * 降到 0 或差额，不再是 {@code lessonCount × pricePerLesson} 的全额。
 */
public record CourseConsumptionSummaryRow(Long courseId, String courseName, BigDecimal pricePerLesson,
                                           int lessonCount, BigDecimal amount, int coveredByBalanceCount,
                                           List<LocalDate> billableDates, boolean settledByRecharge) {
}
