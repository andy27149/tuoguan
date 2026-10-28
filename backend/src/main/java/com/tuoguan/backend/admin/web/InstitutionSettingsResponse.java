package com.tuoguan.backend.admin.web;

import java.math.BigDecimal;

public record InstitutionSettingsResponse(Long id, String name, String logoUrl, boolean custodyEnabled,
                                           boolean offCampusEnabled, boolean billTuitionInAdvance,
                                           BigDecimal offCampusMealRatePerDay) {
}
