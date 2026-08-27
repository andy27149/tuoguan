package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminTeacherService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminTeacherController {

    private final AdminTeacherService adminTeacherService;

    public AdminTeacherController(AdminTeacherService adminTeacherService) {
        this.adminTeacherService = adminTeacherService;
    }

    @PostMapping("/api/admin/teachers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TeacherResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                   @Valid @RequestBody CreateTeacherRequest request) {
        return TeacherResponse.from(
                adminTeacherService.createTeacher(principal.institutionId(), request.phone(), request.name(),
                        request.initialPassword()));
    }

    @GetMapping("/api/admin/teachers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TeacherResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminTeacherService.listTeachers(principal.institutionId()).stream()
                .map(TeacherResponse::from)
                .toList();
    }
}
