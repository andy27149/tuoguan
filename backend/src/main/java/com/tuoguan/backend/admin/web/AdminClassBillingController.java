package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.domain.ClassBillingRate;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.service.BillGenerationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/admin/classes/{classId}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminClassBillingController {

    private final BillGenerationService billGenerationService;

    public AdminClassBillingController(BillGenerationService billGenerationService) {
        this.billGenerationService = billGenerationService;
    }

    @GetMapping("/billing-rate")
    public ClassBillingRate getBillingRate(@AuthenticationPrincipal TeacherPrincipal principal,
                                            @PathVariable Long classId) {
        return billGenerationService.getBillingRate(principal.institutionId(), classId).orElse(null);
    }

    @PutMapping("/billing-rate")
    public ClassBillingRate upsertBillingRate(@AuthenticationPrincipal TeacherPrincipal principal,
                                               @PathVariable Long classId,
                                               @Valid @RequestBody BillingRateRequest request) {
        return billGenerationService.upsertBillingRate(principal.institutionId(), classId,
                request.tuitionRatePerMonth(), request.mealRatePerDay());
    }

    @GetMapping("/bills")
    public List<MonthlyBill> listBills(@AuthenticationPrincipal TeacherPrincipal principal,
                                        @PathVariable Long classId, @RequestParam String month) {
        return billGenerationService.listClassBills(principal.institutionId(), classId, YearMonth.parse(month));
    }

    @PostMapping("/bills/generate")
    public List<MonthlyBill> generateBills(@AuthenticationPrincipal TeacherPrincipal principal,
                                            @PathVariable Long classId, @RequestParam String month) {
        return billGenerationService.generateBillsForClass(principal.institutionId(), classId, YearMonth.parse(month));
    }
}
