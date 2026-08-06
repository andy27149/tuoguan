package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.roster.domain.ClassRoom;

public record ClassRoomResponse(Long id, String name) {

    public static ClassRoomResponse from(ClassRoom classRoom) {
        return new ClassRoomResponse(classRoom.id(), classRoom.name());
    }
}
