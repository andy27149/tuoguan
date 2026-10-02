package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.unit.domain.TeachingUnit;

public record ClassRoomResponse(Long id, String name) {

    public static ClassRoomResponse from(TeachingUnit teachingUnit) {
        return new ClassRoomResponse(teachingUnit.id(), teachingUnit.name());
    }
}
