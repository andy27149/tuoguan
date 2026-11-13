package com.tuoguan.backend.monthlyquickbill.web;

import java.util.List;

public record QuickBillStudentResponse(Long studentId, String name, List<QuickBillCourseResponse> courses) {
}
