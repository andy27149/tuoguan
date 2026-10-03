package com.tuoguan.backend.unit.web;

import java.math.BigDecimal;

public record UpdateTeachingUnitRequest(String name, Long teacherId, BigDecimal pricePerLesson, Boolean active) {
}
