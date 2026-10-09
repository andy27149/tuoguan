package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

// courseIds: 该生要报名的课外课（LESSON_COUNT 教学单元）id 列表，可为空/null（纯托管或暂不报课外课）。
public record AdminCreateStudentRequest(@NotBlank String name, String schoolClassName, Long teachingUnitId,
                                         List<Long> courseIds) {
}
