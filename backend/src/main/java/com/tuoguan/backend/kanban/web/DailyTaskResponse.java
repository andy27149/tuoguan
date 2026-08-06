package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.kanban.domain.DailyTask;

public record DailyTaskResponse(Long id, Long studentId, String subject, String name, boolean custom,
                                 boolean completed) {

    public static DailyTaskResponse from(DailyTask dailyTask) {
        return new DailyTaskResponse(dailyTask.id(), dailyTask.studentId(), dailyTask.subject(), dailyTask.name(),
                dailyTask.custom(), dailyTask.completed());
    }
}
