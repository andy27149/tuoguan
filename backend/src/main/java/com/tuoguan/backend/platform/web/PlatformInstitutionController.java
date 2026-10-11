package com.tuoguan.backend.platform.web;

import com.tuoguan.backend.auth.security.TeacherPrincipal;
import com.tuoguan.backend.platform.service.PlatformInstitutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PlatformInstitutionController {

    private final PlatformInstitutionService platformInstitutionService;

    public PlatformInstitutionController(PlatformInstitutionService platformInstitutionService) {
        this.platformInstitutionService = platformInstitutionService;
    }

    @PostMapping("/api/platform/institutions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformInstitutionResponse create(@AuthenticationPrincipal TeacherPrincipal principal,
                                               @Valid @RequestBody CreatePlatformInstitutionRequest request) {
        return platformInstitutionService.createInstitution(
                request.institutionName(), request.adminPhone(), request.adminInitialPassword(),
                request.contactName(), request.contactPhone(), request.address(), request.trialValidUntil(),
                principal.teacherId());
    }

    @GetMapping("/api/platform/institutions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<PlatformInstitutionSummary> list() {
        return platformInstitutionService.listInstitutions();
    }

    @PostMapping("/api/platform/institutions/{institutionId}/renewals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformInstitutionSummary createRenewal(@AuthenticationPrincipal TeacherPrincipal principal,
                                                      @PathVariable Long institutionId,
                                                      @Valid @RequestBody CreateRenewalRequest request) {
        return platformInstitutionService.recordRenewal(institutionId, request.plan(), request.amount(),
                request.validUntil(), request.note(), principal.teacherId());
    }

    @GetMapping("/api/platform/institutions/{institutionId}/renewals")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<RenewalRecordRow> listRenewals(@PathVariable Long institutionId) {
        return platformInstitutionService.getRenewalHistory(institutionId);
    }

    @PatchMapping("/api/platform/institutions/{institutionId}/contact")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public PlatformInstitutionSummary updateContact(@PathVariable Long institutionId,
                                                      @Valid @RequestBody UpdateInstitutionContactRequest request) {
        return platformInstitutionService.updateContact(institutionId, request.contactName(),
                request.contactPhone(), request.address());
    }
}
