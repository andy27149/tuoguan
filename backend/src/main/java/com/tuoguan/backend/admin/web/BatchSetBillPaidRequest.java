package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.billing.domain.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchSetBillPaidRequest(@NotEmpty List<Long> billIds, boolean isPaid, PaymentMethod paymentMethod) {
}
