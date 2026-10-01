package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.admin.service.AdminInstitutionService;
import com.tuoguan.backend.auth.security.TeacherPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/api/admin/institution")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInstitutionController {

    private final AdminInstitutionService adminInstitutionService;

    public AdminInstitutionController(AdminInstitutionService adminInstitutionService) {
        this.adminInstitutionService = adminInstitutionService;
    }

    @GetMapping
    public InstitutionSettingsResponse get(@AuthenticationPrincipal TeacherPrincipal principal) {
        return adminInstitutionService.getSettings(principal.institutionId());
    }

    @PutMapping("/name")
    public InstitutionSettingsResponse updateName(@AuthenticationPrincipal TeacherPrincipal principal,
                                                   @Valid @RequestBody UpdateInstitutionNameRequest request) {
        return adminInstitutionService.updateName(principal.institutionId(), request.name());
    }

    @PutMapping("/feature-flags")
    public InstitutionSettingsResponse updateFeatureFlags(@AuthenticationPrincipal TeacherPrincipal principal,
                                                            @Valid @RequestBody UpdateFeatureFlagsRequest request) {
        return adminInstitutionService.updateFeatureFlags(principal.institutionId(), request.custodyEnabled(),
                request.offCampusEnabled());
    }

    @PostMapping("/logo")
    public InstitutionSettingsResponse uploadLogo(@AuthenticationPrincipal TeacherPrincipal principal,
                                                   @RequestParam("file") MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return adminInstitutionService.updateLogo(principal.institutionId(), file.getContentType(), content);
    }
}
