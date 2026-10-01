package com.tuoguan.backend.course.web;

import com.tuoguan.backend.course.domain.CourseConsumptionRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConsumptionRecordResponse(Long id, Long studentId, Long courseId, String courseName,
                                         LocalDate consumptionDate, BigDecimal priceSnapshot, String teacherName) {

    public static ConsumptionRecordResponse from(CourseConsumptionRecord record) {
        return from(record, null, null);
    }

    public static ConsumptionRecordResponse from(CourseConsumptionRecord record, String courseName,
                                                  String teacherName) {
        return new ConsumptionRecordResponse(record.id(), record.studentId(), record.courseId(), courseName,
                record.consumptionDate(), record.priceSnapshot(), teacherName);
    }
}
