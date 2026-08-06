package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.roster.domain.TaskTemplate;

public record TaskTemplateResponse(Long id, String subject, String name) {

    public static TaskTemplateResponse from(TaskTemplate taskTemplate) {
        return new TaskTemplateResponse(taskTemplate.id(), taskTemplate.subject(), taskTemplate.name());
    }
}
