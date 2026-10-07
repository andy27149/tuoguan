package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.billing.domain.StudentLeaveRecord;

public record StudentLeaveRecordResponse(Long studentId, String reason) {

    public static StudentLeaveRecordResponse from(StudentLeaveRecord record) {
        return new StudentLeaveRecordResponse(record.studentId(), record.reason());
    }
}
