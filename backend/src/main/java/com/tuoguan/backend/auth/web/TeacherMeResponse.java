package com.tuoguan.backend.auth.web;

import com.tuoguan.backend.auth.domain.Role;

public record TeacherMeResponse(Long id, String phone, Long institutionId, Role role) {
}
