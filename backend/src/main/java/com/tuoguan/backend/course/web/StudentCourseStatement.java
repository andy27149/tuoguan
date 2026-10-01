package com.tuoguan.backend.course.web;

import java.util.List;

public record StudentCourseStatement(List<CourseBalanceRow> balances, List<RechargeRecordResponse> recharges,
                                      List<ConsumptionRecordResponse> consumptions) {
}
