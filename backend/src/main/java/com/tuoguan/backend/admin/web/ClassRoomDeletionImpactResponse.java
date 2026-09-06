package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminClassRoomService.ClassRoomDeletionImpact;

public record ClassRoomDeletionImpactResponse(int studentCount) {

    public static ClassRoomDeletionImpactResponse from(ClassRoomDeletionImpact impact) {
        return new ClassRoomDeletionImpactResponse(impact.studentCount());
    }
}
