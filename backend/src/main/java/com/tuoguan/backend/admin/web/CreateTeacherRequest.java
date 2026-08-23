package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotBlank;

public record CreateTeacherRequest(@NotBlank String phone, @NotBlank String initialPassword) {
}
