package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminStudentService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminStudentController {

    private final AdminStudentService adminStudentService;

    public AdminStudentController(AdminStudentService adminStudentService) {
        this.adminStudentService = adminStudentService;
    }

    @GetMapping("/api/admin/students")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminStudentResponse> list(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminStudentService.listStudents(principal.institutionId());
    }
}
