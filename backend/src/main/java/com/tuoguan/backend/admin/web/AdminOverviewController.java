package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.service.BillGenerationService;
import com.tuoguan.backend.course.service.CourseAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {

    private final CourseAccountService courseAccountService;
    private final BillGenerationService billGenerationService;

    public AdminOverviewController(CourseAccountService courseAccountService,
                                    BillGenerationService billGenerationService) {
        this.courseAccountService = courseAccountService;
        this.billGenerationService = billGenerationService;
    }

    @GetMapping("/low-balance")
    public List<LowBalanceRow> lowBalance(@AuthenticationPrincipal TeacherPrincipal principal) {
        return courseAccountService.getLowBalanceEntries(principal.institutionId(), 3);
    }

    @GetMapping("/unpaid-bills")
    public UnpaidBillSummary unpaidBills(@AuthenticationPrincipal TeacherPrincipal principal) {
        return billGenerationService.getUnpaidBillSummary(principal.institutionId());
    }
}
