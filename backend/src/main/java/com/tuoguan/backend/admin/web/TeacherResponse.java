package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;

import java.time.Instant;

public record TeacherResponse(Long id, String phone, String name, Role role, boolean mustChangePassword,
                               Instant createdAt) {

    public static TeacherResponse from(Teacher teacher) {
        return new TeacherResponse(teacher.id(), teacher.phone(), teacher.name(), teacher.role(),
                teacher.mustChangePassword(), teacher.createdAt());
    }
}
