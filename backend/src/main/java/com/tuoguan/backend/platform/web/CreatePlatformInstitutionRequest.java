package com.tuoguan.backend.platform.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePlatformInstitutionRequest(@NotBlank String institutionName, @NotBlank String adminPhone,
                                                 @NotBlank String adminInitialPassword,
                                                 String contactName,
                                                 @NotBlank String contactPhone,
                                                 String address,
                                                 @NotNull LocalDate trialValidUntil) {
}
