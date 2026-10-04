package com.tuoguan.backend.course.web;

import com.tuoguan.backend.course.domain.CourseConsumptionRecord;

/**
 * 一条消课记录的覆盖判定结果（产品诊断 #02 统一方案）：{@code coveredByBalance=true}
 * 表示这次消课发生时预充值余额充足，已经通过充值付过钱了，不应该再计入月度账单；
 * {@code false} 表示余额不足（或该生从不充值，比如有托管班的学生），需要计入账单收费。
 * 由 {@link com.tuoguan.backend.course.service.CourseAccountService#classifyConsumptions}
 * 按时间顺序回放充值+消课事件计算得出，不存储、每次读时现算。
 */
public record ConsumptionCoverage(CourseConsumptionRecord record, boolean coveredByBalance) {
}
