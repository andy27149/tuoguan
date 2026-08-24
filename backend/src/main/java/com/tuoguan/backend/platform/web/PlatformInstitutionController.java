package com.tuoguan.backend.platform.web;

import com.tuoguan.backend.platform.service.PlatformInstitutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    public PlatformInstitutionResponse create(@Valid @RequestBody CreatePlatformInstitutionRequest request) {
        return platformInstitutionService.createInstitution(
                request.institutionName(), request.adminPhone(), request.adminInitialPassword());
    }

    @GetMapping("/api/platform/institutions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public List<PlatformInstitutionSummary> list() {
        return platformInstitutionService.listInstitutions();
    }
}
