package com.tuoguan.backend.platform.web;

import jakarta.validation.constraints.NotBlank;

public record UpdateInstitutionContactRequest(String contactName, @NotBlank String contactPhone, String address) {
}
