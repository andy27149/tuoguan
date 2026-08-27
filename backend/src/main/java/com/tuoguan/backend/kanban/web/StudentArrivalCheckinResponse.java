package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.kanban.domain.StudentArrivalCheckin;

public record StudentArrivalCheckinResponse(Long studentId, String arrivedAt) {

    public static StudentArrivalCheckinResponse from(StudentArrivalCheckin checkin) {
        return new StudentArrivalCheckinResponse(checkin.studentId(), checkin.arrivedAt());
    }
}
