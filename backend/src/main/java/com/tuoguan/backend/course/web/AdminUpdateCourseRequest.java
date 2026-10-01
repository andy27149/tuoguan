package com.tuoguan.backend.course.web;

import java.math.BigDecimal;

public record AdminUpdateCourseRequest(BigDecimal pricePerLesson, Long teacherId, Boolean active) {
}
