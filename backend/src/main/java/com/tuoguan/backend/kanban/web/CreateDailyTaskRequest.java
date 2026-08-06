package com.tuoguan.backend.kanban.web;

import java.time.LocalDate;

public record CreateDailyTaskRequest(Long taskTemplateId, String subject, String name, LocalDate date) {
}
