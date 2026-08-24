package com.tuoguan.backend.platform.service;

import com.tuoguan.backend.admin.web.DuplicatePhoneException;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.platform.web.PlatformInstitutionResponse;
import com.tuoguan.backend.platform.web.PlatformInstitutionSummary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlatformInstitutionService {

    private final InstitutionDao institutionDao;
    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;

    public PlatformInstitutionService(InstitutionDao institutionDao, TeacherDao teacherDao,
                                       PasswordEncoder passwordEncoder) {
        this.institutionDao = institutionDao;
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
    }

    public PlatformInstitutionResponse createInstitution(String institutionName, String adminPhone,
                                                           String adminInitialPassword) {
        if (teacherDao.findByPhone(adminPhone).isPresent()) {
            throw new DuplicatePhoneException("Phone already registered: " + adminPhone);
        }
        Long institutionId = institutionDao.insert(institutionName);
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode(adminInitialPassword), Role.ADMIN, true, null));
        return new PlatformInstitutionResponse(institutionId, institutionName, adminPhone);
    }

    public List<PlatformInstitutionSummary> listInstitutions() {
        List<Institution> institutions = institutionDao.findAll();
        return institutions.stream()
                .map(institution -> new PlatformInstitutionSummary(
                        institution.id(),
                        institution.name(),
                        institution.createdAt(),
                        teacherDao.findAllByInstitutionId(institution.id()).size()))
                .toList();
    }
}
