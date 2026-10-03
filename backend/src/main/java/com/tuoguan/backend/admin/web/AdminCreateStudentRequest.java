package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotBlank;

public record AdminCreateStudentRequest(@NotBlank String name, String schoolClassName, Long teachingUnitId) {
}
