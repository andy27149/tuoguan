package com.tuoguan.backend.admin.web;

import jakarta.validation.constraints.NotNull;

public record UpdateFeatureFlagsRequest(@NotNull Boolean custodyEnabled, @NotNull Boolean offCampusEnabled) {
}
