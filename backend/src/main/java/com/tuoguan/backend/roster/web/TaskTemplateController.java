package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.roster.service.TaskTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaskTemplateController {

    private final TaskTemplateService taskTemplateService;

    public TaskTemplateController(TaskTemplateService taskTemplateService) {
        this.taskTemplateService = taskTemplateService;
    }

    @PostMapping("/api/task-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskTemplateResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                        @RequestBody CreateTaskTemplateRequest request) {
        return TaskTemplateResponse.from(
                taskTemplateService.create(principal.institutionId(), request.subject(), request.name()));
    }

    @GetMapping("/api/task-templates")
    public List<TaskTemplateResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return taskTemplateService.list(principal.institutionId()).stream()
                .map(TaskTemplateResponse::from)
                .toList();
    }

    @DeleteMapping("/api/task-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long id) {
        taskTemplateService.delete(principal.institutionId(), id);
    }
}
