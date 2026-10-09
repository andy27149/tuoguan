package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

// teachingUnitId: null = 纯课外课（不挂靠托管班），非 null = 转到该托管班。只有管理员端的编辑
// 接口允许改这个字段（转班）——老师端的 UpdateStudentRequest 没有这个字段，教师不能把学生转给
// 别的老师。
// courseIds: 该生当前应该报名的课外课（LESSON_COUNT 教学单元）id 完整列表（全量覆盖，而非增量）。
// 只在本机构"启用中"的课外课范围内做增删同步——已停用课程不会出现在管理员的勾选列表里，所以
// 不会因为它不在这个列表里就被误判为要取消报名，见 AdminStudentService.syncCourseEnrollments。
public record AdminUpdateStudentRequest(@NotBlank String name, String schoolClassName, Long teachingUnitId,
                                         boolean enrolled, List<Long> courseIds) {
}
