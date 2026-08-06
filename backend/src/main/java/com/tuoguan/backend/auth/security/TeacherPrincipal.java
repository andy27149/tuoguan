package com.tuoguan.backend.auth.security;

import com.tuoguan.backend.auth.domain.Role;

public record TeacherPrincipal(Long teacherId, Long institutionId, Role role) {
}
