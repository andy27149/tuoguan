package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAdminClassRequest(@NotBlank String name, @NotNull Long teacherId) {
}
