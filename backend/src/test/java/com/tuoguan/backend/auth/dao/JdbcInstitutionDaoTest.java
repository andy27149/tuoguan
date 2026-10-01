package com.tuoguan.backend.auth.dao;

import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcInstitutionDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Test
    void insertAndFindByIdRoundTrips() {
        Long id = institutionDao.insert("测试机构A");

        Optional<Institution> found = institutionDao.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("测试机构A");
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<Institution> found = institutionDao.findById(-1L);

        assertThat(found).isEmpty();
    }

    @Test
    void newInstitutionDefaultsToBothFeatureFlagsEnabled() {
        Long id = institutionDao.insert("测试机构B");

        Institution found = institutionDao.findById(id).orElseThrow();

        assertThat(found.custodyEnabled()).isTrue();
        assertThat(found.offCampusEnabled()).isTrue();
    }

    @Test
    void updateFeatureFlagsPersistsBothValues() {
        Long id = institutionDao.insert("测试机构C");

        institutionDao.updateFeatureFlags(id, false, true);

        Institution found = institutionDao.findById(id).orElseThrow();
        assertThat(found.custodyEnabled()).isFalse();
        assertThat(found.offCampusEnabled()).isTrue();
    }
}
