package com.tuoguan.backend.course.web;

import java.time.LocalDate;
import java.util.List;

/**
 * 托管班学生同时报名课外课时的轻量展示：只列课程名+最近消课日期，不带充值/余额——
 * 这类学生的课外课费用走托管月度账单附加费，从不走预充值，{@link CourseBalanceRow} 的
 * 「余额」概念对他们不成立（会显示负数，容易让家长误以为欠费）。见产品诊断 #06。
 */
public record CourseActivityRow(Long courseId, String courseName, List<LocalDate> recentConsumptionDates) {
}
