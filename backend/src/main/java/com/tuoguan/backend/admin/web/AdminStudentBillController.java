package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.service.BillGenerationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/admin/students/{studentId}/bills")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentBillController {

    private final BillGenerationService billGenerationService;

    public AdminStudentBillController(BillGenerationService billGenerationService) {
        this.billGenerationService = billGenerationService;
    }

    @PostMapping("/generate")
    public MonthlyBill generate(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long studentId,
                                 @RequestParam String month,
                                 @RequestBody(required = false) GenerateBillRequest request) {
        return billGenerationService.generateBill(principal.institutionId(), studentId, YearMonth.parse(month),
                request != null ? request.tuitionOverride() : null);
    }
}
