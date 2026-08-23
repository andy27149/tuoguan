package com.tuoguan.backend.admin.web;

import java.util.List;

public record AdminDashboardResponse(String date, List<ClassSummary> classes) {

    public record ClassSummary(Long classRoomId, String className, int studentCount, int completedStudentCount) {
    }
}
