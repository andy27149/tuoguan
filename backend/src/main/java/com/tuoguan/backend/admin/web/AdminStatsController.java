package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminStatsService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/api/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardResponse dashboard(@AuthenticationPrincipal TeacherPrincipal principal,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return new AdminDashboardResponse(effectiveDate.toString(),
                adminStatsService.getDashboard(principal.institutionId(), effectiveDate));
    }
}
