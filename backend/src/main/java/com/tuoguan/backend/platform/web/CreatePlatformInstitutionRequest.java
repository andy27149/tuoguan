package com.tuoguan.backend.platform.web;

import jakarta.validation.constraints.NotBlank;

public record CreatePlatformInstitutionRequest(@NotBlank String institutionName, @NotBlank String adminPhone,
                                                 @NotBlank String adminInitialPassword) {
}
