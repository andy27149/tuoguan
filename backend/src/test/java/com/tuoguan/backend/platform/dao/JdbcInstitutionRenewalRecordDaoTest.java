package com.tuoguan.backend.platform.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.platform.domain.InstitutionRenewalRecord;
import com.tuoguan.backend.platform.domain.RenewalPlan;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcInstitutionRenewalRecordDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionRenewalRecordDao renewalRecordDao;

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Test
    void insertAndFindAllByInstitutionIdReturnsNewestFirst() {
        Long institutionId = institutionDao.insert("续费记录测试机构A");
        Long platformAdminId = teacherDao.insert(new Teacher(null, null, "13900029001",
                "hash", Role.PLATFORM_ADMIN, false, null));

        renewalRecordDao.insert(institutionId, RenewalPlan.TRIAL, null,
                LocalDate.of(2026, 11, 1), null, platformAdminId);
        renewalRecordDao.insert(institutionId, RenewalPlan.MONTHLY, new BigDecimal("299.00"),
                LocalDate.of(2026, 12, 1), "首次续费", platformAdminId);

        List<InstitutionRenewalRecord> records = renewalRecordDao.findAllByInstitutionId(institutionId);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).plan()).isEqualTo(RenewalPlan.MONTHLY);
        assertThat(records.get(0).amount()).isEqualByComparingTo("299.00");
        assertThat(records.get(0).note()).isEqualTo("首次续费");
        assertThat(records.get(1).plan()).isEqualTo(RenewalPlan.TRIAL);
        assertThat(records.get(1).amount()).isNull();
    }

    @Test
    void findLatestByInstitutionIdReturnsEmptyWhenNoRecords() {
        Long institutionId = institutionDao.insert("续费记录测试机构B");

        Optional<InstitutionRenewalRecord> latest = renewalRecordDao.findLatestByInstitutionId(institutionId);

        assertThat(latest).isEmpty();
    }

    @Test
    void findLatestByInstitutionIdReturnsMostRecentRecord() {
        Long institutionId = institutionDao.insert("续费记录测试机构C");
        Long platformAdminId = teacherDao.insert(new Teacher(null, null, "13900029002",
                "hash", Role.PLATFORM_ADMIN, false, null));
        renewalRecordDao.insert(institutionId, RenewalPlan.TRIAL, null,
                LocalDate.of(2026, 11, 1), null, platformAdminId);
        renewalRecordDao.insert(institutionId, RenewalPlan.YEARLY, new BigDecimal("2899.00"),
                LocalDate.of(2027, 11, 1), null, platformAdminId);

        InstitutionRenewalRecord latest = renewalRecordDao.findLatestByInstitutionId(institutionId).orElseThrow();

        assertThat(latest.plan()).isEqualTo(RenewalPlan.YEARLY);
        assertThat(latest.validUntil()).isEqualTo(LocalDate.of(2027, 11, 1));
    }
}
