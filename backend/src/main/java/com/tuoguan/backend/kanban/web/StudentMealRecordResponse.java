package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.kanban.domain.StudentMealRecord;

public record StudentMealRecordResponse(Long studentId) {

    public static StudentMealRecordResponse from(StudentMealRecord record) {
        return new StudentMealRecordResponse(record.studentId());
    }
}
