package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotBlank;

// teachingUnitId: null = 纯课外课（不挂靠托管班），非 null = 转到该托管班。只有管理员端的编辑
// 接口允许改这个字段（转班）——老师端的 UpdateStudentRequest 没有这个字段，教师不能把学生转给
// 别的老师。
public record AdminUpdateStudentRequest(@NotBlank String name, String schoolClassName, Long teachingUnitId,
                                         boolean enrolled) {
}
