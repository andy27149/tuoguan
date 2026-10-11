package com.tuoguan.backend.platform.dao;

import com.tuoguan.backend.platform.domain.InstitutionRenewalRecord;
import com.tuoguan.backend.platform.domain.RenewalPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstitutionRenewalRecordDao {

    Long insert(Long institutionId, RenewalPlan plan, BigDecimal amount, LocalDate validUntil, String note,
                Long recordedByTeacherId);

    List<InstitutionRenewalRecord> findAllByInstitutionId(Long institutionId);

    Optional<InstitutionRenewalRecord> findLatestByInstitutionId(Long institutionId);
}
