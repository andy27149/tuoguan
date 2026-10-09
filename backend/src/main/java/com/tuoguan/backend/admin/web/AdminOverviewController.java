package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminStatsService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.service.BillGenerationService;
import com.tuoguan.backend.course.service.CourseAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {

    private final CourseAccountService courseAccountService;
    private final BillGenerationService billGenerationService;
    private final AdminStatsService adminStatsService;

    public AdminOverviewController(CourseAccountService courseAccountService,
                                    BillGenerationService billGenerationService,
                                    AdminStatsService adminStatsService) {
        this.courseAccountService = courseAccountService;
        this.billGenerationService = billGenerationService;
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/low-balance")
    public List<LowBalanceRow> lowBalance(@AuthenticationPrincipal TeacherPrincipal principal) {
        return courseAccountService.getLowBalanceEntries(principal.institutionId(), 3);
    }

    @GetMapping("/unpaid-bills")
    public UnpaidBillSummary unpaidBills(@AuthenticationPrincipal TeacherPrincipal principal) {
        return billGenerationService.getUnpaidBillSummary(principal.institutionId());
    }

    @GetMapping("/enrollment")
    public EnrollmentSummary enrollment(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminStatsService.getEnrollmentSummary(principal.institutionId());
    }

    @GetMapping("/today")
    public TodaySnapshot today(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminStatsService.getTodaySnapshot(principal.institutionId(), LocalDate.now());
    }

    @GetMapping("/revenue")
    public RevenueSnapshot revenue(@AuthenticationPrincipal TeacherPrincipal principal) {
        return billGenerationService.getRevenueSnapshot(principal.institutionId());
    }
}
