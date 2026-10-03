package com.tuoguan.backend.admin.service;

import com.tuoguan.backend.admin.web.InstitutionSettingsResponse;
import com.tuoguan.backend.admin.web.InvalidFeatureFlagsException;
import com.tuoguan.backend.admin.web.InvalidLogoException;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.storage.StorageService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AdminInstitutionService {

    private static final Set<String> ALLOWED_LOGO_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final InstitutionDao institutionDao;
    private final StorageService storageService;

    public AdminInstitutionService(InstitutionDao institutionDao, StorageService storageService) {
        this.institutionDao = institutionDao;
        this.storageService = storageService;
    }

    public InstitutionSettingsResponse getSettings(Long institutionId) {
        return toResponse(requireInstitution(institutionId));
    }

    public InstitutionSettingsResponse updateName(Long institutionId, String name) {
        requireInstitution(institutionId);
        institutionDao.updateName(institutionId, name);
        return toResponse(requireInstitution(institutionId));
    }

    /**
     * 注意：{@code custodyEnabled}/{@code offCampusEnabled} 目前只是 UI 层面的软开关——
     * 这里只校验「不能同时关闭」，不会级联禁用后端接口。关闭托管/课外课后，
     * {@code com.tuoguan.backend.unit.web.AdminTeachingUnitController} 等相关接口仍可被
     * 直接调用成功（前端只是不再显示入口，见 {@code AdminSidebar.tsx} 的 isModuleHidden）。
     * 当前风险低（无对外 API），接入对外 API 对接前需要在这里补充机构级硬校验。见产品诊断 #07。
     */
    public InstitutionSettingsResponse updateFeatureFlags(Long institutionId, boolean custodyEnabled,
                                                            boolean offCampusEnabled) {
        if (!custodyEnabled && !offCampusEnabled) {
            throw new InvalidFeatureFlagsException("At least one of custody or off-campus must remain enabled");
        }
        requireInstitution(institutionId);
        institutionDao.updateFeatureFlags(institutionId, custodyEnabled, offCampusEnabled);
        return toResponse(requireInstitution(institutionId));
    }

    public InstitutionSettingsResponse updateLogo(Long institutionId, String contentType, byte[] content) {
        if (!ALLOWED_LOGO_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidLogoException("Unsupported logo content type: " + contentType);
        }
        Institution existing = requireInstitution(institutionId);
        String objectKey = storageService.uploadLogo(institutionId, contentType, content);
        institutionDao.updateLogoObjectKey(institutionId, objectKey);
        String previousObjectKey = existing.logoObjectKey();
        if (previousObjectKey != null) {
            storageService.delete(previousObjectKey);
        }
        return toResponse(requireInstitution(institutionId));
    }

    private Institution requireInstitution(Long institutionId) {
        return institutionDao.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Institution not found: " + institutionId));
    }

    private InstitutionSettingsResponse toResponse(Institution institution) {
        return new InstitutionSettingsResponse(institution.id(), institution.name(),
                storageService.avatarUrl(institution.logoObjectKey()), institution.custodyEnabled(),
                institution.offCampusEnabled());
    }
}
