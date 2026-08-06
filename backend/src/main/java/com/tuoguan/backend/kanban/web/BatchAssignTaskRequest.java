package com.tuoguan.backend.kanban.web;

import java.time.LocalDate;
import java.util.List;

public record BatchAssignTaskRequest(List<Long> taskTemplateIds, LocalDate date) {
}
