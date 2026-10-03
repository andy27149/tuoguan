package com.tuoguan.backend.share.web;

import com.tuoguan.backend.course.web.CourseActivityRow;
import com.tuoguan.backend.course.web.StudentCourseStatement;
import com.tuoguan.backend.kanban.web.MonthlyStatsResponse;

import java.util.List;

public record PublicShareResponse(String studentName, String schoolClassName, String avatarUrl,
                                   MonthlyStatsResponse stats, StudentCourseStatement courseStatement,
                                   List<CourseActivityRow> courseActivity) {
}
