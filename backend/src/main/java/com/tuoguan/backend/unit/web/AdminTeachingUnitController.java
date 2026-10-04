package com.tuoguan.backend.unit.web;

import com.tuoguan.backend.audit.service.AuditLogService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.service.AdminTeachingUnitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminTeachingUnitController {

    private final AdminTeachingUnitService adminTeachingUnitService;
    private final AuditLogService auditLogService;

    public AdminTeachingUnitController(AdminTeachingUnitService adminTeachingUnitService,
                                        AuditLogService auditLogService) {
        this.adminTeachingUnitService = adminTeachingUnitService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/api/admin/teaching-units")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminTeachingUnitResponse> list(@AuthenticationPrincipal TeacherPrincipal principal,
                                                 @RequestParam(required = false) BillingMode billingMode) {
        return adminTeachingUnitService.list(principal.institutionId(), billingMode);
    }

    @PostMapping("/api/admin/teaching-units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminTeachingUnitResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                             @Valid @RequestBody CreateTeachingUnitRequest request) {
        return adminTeachingUnitService.create(principal.institutionId(), request);
    }

    @PatchMapping("/api/admin/teaching-units/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminTeachingUnitResponse update(@AuthenticationPrincipal TeacherPrincipal principal,
                                             @PathVariable Long id,
                                             @RequestBody UpdateTeachingUnitRequest request) {
        AdminTeachingUnitResponse response = adminTeachingUnitService.update(principal.institutionId(), id, request);
        if (request.active() != null) {
            auditLogService.record(principal.institutionId(), principal.teacherId(),
                    request.active() ? "TEACHING_UNIT_ACTIVATE" : "TEACHING_UNIT_DEACTIVATE", "TEACHING_UNIT", id,
                    null);
        }
        return response;
    }

    @GetMapping("/api/admin/teaching-units/{id}/deletion-impact")
    @PreAuthorize("hasRole('ADMIN')")
    public TeachingUnitDeletionImpactResponse deletionImpact(@AuthenticationPrincipal TeacherPrincipal principal,
                                                              @PathVariable Long id) {
        return TeachingUnitDeletionImpactResponse.from(
                adminTeachingUnitService.getDeletionImpact(principal.institutionId(), id));
    }

    @DeleteMapping("/api/admin/teaching-units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal TeacherPrincipal principal, @PathVariable Long id) {
        adminTeachingUnitService.delete(principal.institutionId(), id);
    }
}
