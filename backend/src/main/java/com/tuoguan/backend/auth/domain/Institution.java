package com.tuoguan.backend.auth.domain;

import java.time.Instant;
import java.time.LocalDate;

public record Institution(Long id, String name, String logoObjectKey, boolean custodyEnabled,
                           boolean offCampusEnabled, String contactName, String contactPhone,
                           String address, LocalDate validUntil, Instant createdAt,
                           boolean billTuitionInAdvance) {
}
