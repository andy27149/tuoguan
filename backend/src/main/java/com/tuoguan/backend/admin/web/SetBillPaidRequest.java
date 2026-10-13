package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.billing.domain.PaymentMethod;

// paymentMethod 可空（产品诊断 #14）：由前端 UI 要求用户在标记已缴费时选择，后端保持
// 宽松校验，不强制要求，避免破坏既有"只传 isPaid"的调用方。
public record SetBillPaidRequest(boolean isPaid, PaymentMethod paymentMethod) {
}
