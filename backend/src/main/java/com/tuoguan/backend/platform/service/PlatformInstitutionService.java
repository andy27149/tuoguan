package com.tuoguan.backend.platform.service;

import com.tuoguan.backend.admin.web.DuplicatePhoneException;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.platform.dao.InstitutionRenewalRecordDao;
import com.tuoguan.backend.platform.domain.InstitutionRenewalRecord;
import com.tuoguan.backend.platform.domain.InstitutionStatus;
import com.tuoguan.backend.platform.domain.RenewalPlan;
import com.tuoguan.backend.platform.web.PlatformInstitutionResponse;
import com.tuoguan.backend.platform.web.PlatformInstitutionSummary;
import com.tuoguan.backend.platform.web.RenewalRecordRow;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.web.NotFoundException;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PlatformInstitutionService {

    private static final int EXPIRING_SOON_THRESHOLD_DAYS = 7;

    private final InstitutionDao institutionDao;
    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;
    private final InstitutionRenewalRecordDao renewalRecordDao;
    private final StudentDao studentDao;
    private final TeachingUnitDao teachingUnitDao;

    public PlatformInstitutionService(InstitutionDao institutionDao, TeacherDao teacherDao,
                                       PasswordEncoder passwordEncoder,
                                       InstitutionRenewalRecordDao renewalRecordDao,
                                       StudentDao studentDao, TeachingUnitDao teachingUnitDao) {
        this.institutionDao = institutionDao;
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
        this.renewalRecordDao = renewalRecordDao;
        this.studentDao = studentDao;
        this.teachingUnitDao = teachingUnitDao;
    }

    public PlatformInstitutionResponse createInstitution(String institutionName, String adminPhone,
                                                           String adminInitialPassword, String contactName,
                                                           String contactPhone, String address,
                                                           LocalDate trialValidUntil, Long recordedByTeacherId) {
        if (teacherDao.findByPhone(adminPhone).isPresent()) {
            throw new DuplicatePhoneException("Phone already registered: " + adminPhone);
        }
        Long institutionId = institutionDao.insert(institutionName);
        institutionDao.updateContact(institutionId, contactName, contactPhone, address);
        institutionDao.updateValidUntil(institutionId, trialValidUntil);
        renewalRecordDao.insert(institutionId, RenewalPlan.TRIAL, null, trialValidUntil, null, recordedByTeacherId);
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode(adminInitialPassword), Role.ADMIN, true, null));
        return new PlatformInstitutionResponse(institutionId, institutionName, adminPhone);
    }

    public List<PlatformInstitutionSummary> listInstitutions() {
        return institutionDao.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    public PlatformInstitutionSummary recordRenewal(Long institutionId, RenewalPlan plan, java.math.BigDecimal amount,
                                                      LocalDate validUntil, String note, Long recordedByTeacherId) {
        requireInstitution(institutionId);
        renewalRecordDao.insert(institutionId, plan, amount, validUntil, note, recordedByTeacherId);
        institutionDao.updateValidUntil(institutionId, validUntil);
        return toSummary(requireInstitution(institutionId));
    }

    public List<RenewalRecordRow> getRenewalHistory(Long institutionId) {
        requireInstitution(institutionId);
        return renewalRecordDao.findAllByInstitutionId(institutionId).stream()
                .map(record -> new RenewalRecordRow(record.id(), record.plan(), record.amount(),
                        record.validUntil(), record.note(),
                        teacherDao.findById(record.recordedByTeacherId()).map(Teacher::name).orElse("-"),
                        record.createdAt()))
                .toList();
    }

    public PlatformInstitutionSummary updateContact(Long institutionId, String contactName, String contactPhone,
                                                      String address) {
        requireInstitution(institutionId);
        institutionDao.updateContact(institutionId, contactName, contactPhone, address);
        return toSummary(requireInstitution(institutionId));
    }

    private Institution requireInstitution(Long institutionId) {
        return institutionDao.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("Institution not found: " + institutionId));
    }

    private PlatformInstitutionSummary toSummary(Institution institution) {
        int teacherCount = teacherDao.findAllByInstitutionId(institution.id()).size();
        int studentCount = (int) studentDao.findAllByInstitutionId(institution.id()).stream()
                .filter(Student::enrolled)
                .count();
        int classCount = teachingUnitDao.findAllByInstitutionId(institution.id()).size();
        RenewalPlan currentPlan = renewalRecordDao.findLatestByInstitutionId(institution.id())
                .map(InstitutionRenewalRecord::plan)
                .orElse(null);
        return new PlatformInstitutionSummary(institution.id(), institution.name(), institution.createdAt(),
                teacherCount, studentCount, classCount, institution.contactName(), institution.contactPhone(),
                institution.address(), institution.validUntil(), currentPlan,
                deriveStatus(institution.validUntil()));
    }

    private InstitutionStatus deriveStatus(LocalDate validUntil) {
        if (validUntil == null) {
            return InstitutionStatus.NONE;
        }
        LocalDate today = LocalDate.now();
        if (validUntil.isBefore(today)) {
            return InstitutionStatus.EXPIRED;
        }
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, validUntil);
        if (daysLeft <= EXPIRING_SOON_THRESHOLD_DAYS) {
            return InstitutionStatus.EXPIRING_SOON;
        }
        return InstitutionStatus.ACTIVE;
    }
}
