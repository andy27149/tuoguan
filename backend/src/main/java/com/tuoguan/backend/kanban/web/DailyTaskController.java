package com.tuoguan.backend.kanban.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.service.DailyTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class DailyTaskController {

    private final DailyTaskService dailyTaskService;

    public DailyTaskController(DailyTaskService dailyTaskService) {
        this.dailyTaskService = dailyTaskService;
    }

    @PostMapping("/api/classes/{classId}/daily-tasks/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<DailyTaskResponse> batchAssign(@AuthenticationPrincipal TeacherPrincipal principal,
                                                @PathVariable Long classId,
                                                @RequestBody BatchAssignTaskRequest request) {
        return dailyTaskService.batchAssign(principal.teacherId(), classId, request.taskTemplateIds(),
                        request.date()).stream()
                .map(DailyTaskResponse::from)
                .toList();
    }

    @PostMapping("/api/students/{studentId}/daily-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public DailyTaskResponse addForStudent(@AuthenticationPrincipal TeacherPrincipal principal,
                                            @PathVariable Long studentId,
                                            @RequestBody CreateDailyTaskRequest request) {
        return DailyTaskResponse.from(dailyTaskService.addForStudent(principal.teacherId(), studentId,
                request.taskTemplateId(), request.subject(), request.name(), request.date()));
    }

    @GetMapping("/api/classes/{classId}/daily-tasks")
    public List<DailyTaskResponse> listForClass(@AuthenticationPrincipal TeacherPrincipal principal,
                                                 @PathVariable Long classId,
                                                 @RequestParam LocalDate date) {
        return dailyTaskService.listForClass(principal.teacherId(), classId, date).stream()
                .map(DailyTaskResponse::from)
                .toList();
    }

    @PatchMapping("/api/daily-tasks/{id}")
    public DailyTaskResponse setCompleted(@AuthenticationPrincipal TeacherPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody UpdateDailyTaskRequest request) {
        return DailyTaskResponse.from(
                dailyTaskService.setCompleted(principal.teacherId(), id, request.completed()));
    }

    @DeleteMapping("/api/daily-tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long id) {
        dailyTaskService.delete(principal.teacherId(), id);
    }
}
