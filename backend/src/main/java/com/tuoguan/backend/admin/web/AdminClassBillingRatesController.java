package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.audit.service.AuditLogService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.service.BillGenerationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/classes/billing-rates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminClassBillingRatesController {

    private final BillGenerationService billGenerationService;
    private final AuditLogService auditLogService;

    public AdminClassBillingRatesController(BillGenerationService billGenerationService,
                                             AuditLogService auditLogService) {
        this.billGenerationService = billGenerationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<ClassBillingRateRow> listAll(@AuthenticationPrincipal TeacherPrincipal principal) {
        return billGenerationService.listAllClassBillingRates(principal.institutionId());
    }

    @PutMapping("/bulk-set")
    public List<ClassBillingRateRow> bulkSet(@AuthenticationPrincipal TeacherPrincipal principal,
                                              @Valid @RequestBody BillingRateRequest request) {
        billGenerationService.bulkSetBillingRate(principal.institutionId(), request.tuitionRatePerMonth(),
                request.mealRatePerDay());
        auditLogService.record(principal.institutionId(), principal.teacherId(), "BILLING_RATE_BULK_SET",
                "INSTITUTION", principal.institutionId(),
                "tuitionRatePerMonth=" + request.tuitionRatePerMonth() + ", mealRatePerDay=" + request.mealRatePerDay());
        return billGenerationService.listAllClassBillingRates(principal.institutionId());
    }
}
