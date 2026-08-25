package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.kanban.domain.StudentPickupCheckin;

public record StudentPickupCheckinResponse(Long studentId, String pickedUpBy, String pickedUpAt) {

    public static StudentPickupCheckinResponse from(StudentPickupCheckin checkin) {
        return new StudentPickupCheckinResponse(checkin.studentId(), checkin.pickedUpBy(), checkin.pickedUpAt());
    }
}
