package com.tuoguan.backend.admin.web;

public record LowBalanceRow(Long studentId, String studentName, Long courseId, String courseName, int balance) {
}
