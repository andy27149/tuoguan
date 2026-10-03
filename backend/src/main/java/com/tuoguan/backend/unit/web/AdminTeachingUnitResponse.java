package com.tuoguan.backend.unit.web;

import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;

import java.math.BigDecimal;

public record AdminTeachingUnitResponse(Long id, String name, BillingMode billingMode, Long teacherId,
                                         String teacherName, String teacherPhone, Integer lessonDurationMinutes,
                                         BigDecimal pricePerLesson, boolean active) {

    public static AdminTeachingUnitResponse from(TeachingUnit unit, String teacherName, String teacherPhone) {
        return new AdminTeachingUnitResponse(unit.id(), unit.name(), unit.billingMode(), unit.teacherId(),
                teacherName, teacherPhone, unit.lessonDurationMinutes(), unit.pricePerLesson(), unit.active());
    }
}
