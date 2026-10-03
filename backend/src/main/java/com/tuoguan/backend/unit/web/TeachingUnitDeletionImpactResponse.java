package com.tuoguan.backend.unit.web;

import com.tuoguan.backend.unit.service.AdminTeachingUnitService.TeachingUnitDeletionImpact;

public record TeachingUnitDeletionImpactResponse(int studentCount) {

    public static TeachingUnitDeletionImpactResponse from(TeachingUnitDeletionImpact impact) {
        return new TeachingUnitDeletionImpactResponse(impact.studentCount());
    }
}
