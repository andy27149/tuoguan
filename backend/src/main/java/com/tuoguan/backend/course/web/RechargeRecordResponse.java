package com.tuoguan.backend.course.web;

import com.tuoguan.backend.course.domain.CourseRechargeRecord;

import java.time.Instant;

public record RechargeRecordResponse(Long id, Long studentId, Long courseId, String courseName, Integer lessonCount,
                                      String note, Instant createdAt) {

    public static RechargeRecordResponse from(CourseRechargeRecord record) {
        return from(record, null);
    }

    public static RechargeRecordResponse from(CourseRechargeRecord record, String courseName) {
        return new RechargeRecordResponse(record.id(), record.studentId(), record.courseId(), courseName,
                record.lessonCount(), record.note(), record.createdAt());
    }
}
