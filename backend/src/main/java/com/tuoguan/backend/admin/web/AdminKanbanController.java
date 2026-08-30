package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminKanbanService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.kanban.web.DailyTaskResponse;
import com.tuoguan.backend.kanban.web.DismissalStatusResponse;
import com.tuoguan.backend.kanban.web.StudentArrivalCheckinResponse;
import com.tuoguan.backend.kanban.web.StudentDailyNoteResponse;
import com.tuoguan.backend.roster.service.StudentService;
import com.tuoguan.backend.roster.web.StudentResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/classes/{classId}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKanbanController {

    private final AdminKanbanService adminKanbanService;
    private final StudentService studentService;

    public AdminKanbanController(AdminKanbanService adminKanbanService, StudentService studentService) {
        this.adminKanbanService = adminKanbanService;
        this.studentService = studentService;
    }

    @GetMapping("/students")
    public List<StudentResponse> students(@AuthenticationPrincipal TeacherPrincipal principal,
                                           @PathVariable Long classId) {
        return adminKanbanService.listStudents(principal.institutionId(), classId).stream()
                .map(s -> StudentResponse.from(s, studentService.avatarUrl(s)))
                .toList();
    }

    @GetMapping("/daily-tasks")
    public List<DailyTaskResponse> dailyTasks(@AuthenticationPrincipal TeacherPrincipal principal,
                                               @PathVariable Long classId,
                                               @RequestParam LocalDate date) {
        return adminKanbanService.listDailyTasks(principal.institutionId(), classId, date).stream()
                .map(DailyTaskResponse::from)
                .toList();
    }

    @GetMapping("/dismissal")
    public DismissalStatusResponse dismissal(@AuthenticationPrincipal TeacherPrincipal principal,
                                              @PathVariable Long classId,
                                              @RequestParam LocalDate date) {
        return new DismissalStatusResponse(adminKanbanService.isDismissed(principal.institutionId(), classId, date));
    }

    @GetMapping("/student-notes")
    public List<StudentDailyNoteResponse> notes(@AuthenticationPrincipal TeacherPrincipal principal,
                                                 @PathVariable Long classId,
                                                 @RequestParam LocalDate date) {
        return adminKanbanService.listNotes(principal.institutionId(), classId, date).stream()
                .map(StudentDailyNoteResponse::from)
                .toList();
    }

    @GetMapping("/arrivals")
    public List<StudentArrivalCheckinResponse> arrivals(@AuthenticationPrincipal TeacherPrincipal principal,
                                                          @PathVariable Long classId,
                                                          @RequestParam LocalDate date) {
        return adminKanbanService.listArrivals(principal.institutionId(), classId, date).stream()
                .map(StudentArrivalCheckinResponse::from)
                .toList();
    }
}
