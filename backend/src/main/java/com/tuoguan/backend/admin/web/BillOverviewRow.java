package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.billing.domain.PaymentMethod;

import java.math.BigDecimal;

// overdueDays（产品诊断 #14）：账单没有独立的到期日字段，约定到期日 = 账单所属月份的
// 月底（YearMonth.atEndOfMonth()），未缴费时为"今天-月底"的天数（不早于 0），已缴费
// 或还没生成账单时恒为 0。
public record BillOverviewRow(Long studentId, String studentName, Long classRoomId, String className,
                               String teacherName, Long billId, BigDecimal totalAmount, boolean isPaid,
                               PaymentMethod paymentMethod, String yearMonth, int overdueDays) {
}
