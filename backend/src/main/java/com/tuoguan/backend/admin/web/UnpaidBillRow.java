package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record UnpaidBillRow(Long studentId, String studentName, String className, String yearMonth,
                             BigDecimal totalAmount) {
}
