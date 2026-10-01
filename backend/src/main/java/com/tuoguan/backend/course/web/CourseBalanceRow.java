package com.tuoguan.backend.course.web;

public record CourseBalanceRow(Long courseId, String courseName, int lessonsRecharged, int lessonsConsumed,
                                int balance) {
}
