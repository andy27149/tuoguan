package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.domain.Role;
import jakarta.validation.constraints.NotBlank;

public record CreateTeacherRequest(@NotBlank String phone, @NotBlank String name, @NotBlank String initialPassword,
                                    Role role) {
}
