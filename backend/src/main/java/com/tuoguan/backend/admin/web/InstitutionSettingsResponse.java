package com.tuoguan.backend.admin.web;

public record InstitutionSettingsResponse(Long id, String name, String logoUrl, boolean custodyEnabled,
                                           boolean offCampusEnabled) {
}
