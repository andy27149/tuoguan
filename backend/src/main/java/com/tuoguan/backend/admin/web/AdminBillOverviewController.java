package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.audit.service.AuditLogService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.billing.domain.MonthlyBill;
import com.tuoguan.backend.billing.service.BillGenerationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/admin/bills")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillOverviewController {

    private final BillGenerationService billGenerationService;
    private final AuditLogService auditLogService;

    public AdminBillOverviewController(BillGenerationService billGenerationService, AuditLogService auditLogService) {
        this.billGenerationService = billGenerationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<BillOverviewRow> listOverview(@AuthenticationPrincipal TeacherPrincipal principal,
                                               @RequestParam(required = false) String month,
                                               @RequestParam(required = false) Long classRoomId,
                                               @RequestParam(required = false) String studentName,
                                               @RequestParam(required = false, defaultValue = "false") boolean offCampusOnly) {
        if (month == null || month.isBlank()) {
            return billGenerationService.getBillOverviewAllMonths(principal.institutionId(), classRoomId,
                    studentName, offCampusOnly);
        }
        return billGenerationService.getBillOverview(principal.institutionId(), YearMonth.parse(month), classRoomId,
                studentName, offCampusOnly);
    }

    @GetMapping("/{billId}")
    public MonthlyBill getBill(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long billId) {
        return billGenerationService.getBill(principal.institutionId(), billId);
    }

    @PostMapping("/generate")
    public BulkBillGenerationResult generateBills(@AuthenticationPrincipal TeacherPrincipal principal,
                                                   @RequestParam String month) {
        BulkBillGenerationResult result =
                billGenerationService.generateBillsForInstitution(principal.institutionId(), YearMonth.parse(month));
        auditLogService.record(principal.institutionId(), principal.teacherId(), "BILL_BULK_GENERATE", "INSTITUTION",
                principal.institutionId(), "month=" + month + ", generated=" + result.generatedCount()
                        + ", skipped=" + result.failures().size());
        return result;
    }

    @PatchMapping("/{billId}/paid")
    public MonthlyBill setPaid(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long billId,
                                @Valid @RequestBody SetBillPaidRequest request) {
        MonthlyBill bill = billGenerationService.setBillPaid(principal.institutionId(), billId, request.isPaid());
        auditLogService.record(principal.institutionId(), principal.teacherId(), "BILL_SET_PAID", "MONTHLY_BILL",
                billId, "isPaid=" + request.isPaid());
        return bill;
    }
}
