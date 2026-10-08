package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;
import java.util.List;

public record UnpaidBillSummary(int count, BigDecimal totalAmount, List<UnpaidBillRow> rows) {
}
