package com.tuoguan.backend.platform.web;

import com.tuoguan.backend.platform.domain.InstitutionStatus;
import com.tuoguan.backend.platform.domain.RenewalPlan;

import java.time.Instant;
import java.time.LocalDate;

public record PlatformInstitutionSummary(
        Long id, String name, Instant createdAt, int teacherCount, int studentCount, int classCount,
        String contactName, String contactPhone, String address, LocalDate validUntil,
        RenewalPlan currentPlan, InstitutionStatus status, int todayViewCount) {
}
