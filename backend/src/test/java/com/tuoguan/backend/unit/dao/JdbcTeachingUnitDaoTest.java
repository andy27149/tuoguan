package com.tuoguan.backend.unit.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTeachingUnitDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Test
    void insertAndFindByIdRoundTripsForMonthlyBillingMode() {
        Long institutionId = institutionDao.insert("教学单元测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900001001", "hash",
                Role.TEACHER, false, null));

        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一年级托管班",
                BillingMode.MONTHLY, null, null, true, null));

        Optional<TeachingUnit> found = teachingUnitDao.findById(unitId);
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("一年级托管班");
        assertThat(found.get().teacherId()).isEqualTo(teacherId);
        assertThat(found.get().billingMode()).isEqualTo(BillingMode.MONTHLY);
        assertThat(found.get().lessonDurationMinutes()).isNull();
        assertThat(found.get().pricePerLesson()).isNull();
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void insertAndFindByIdRoundTripsForLessonCountBillingMode() {
        Long institutionId = institutionDao.insert("教学单元测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020001", "hash",
                Role.TEACHER, false, null));

        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("50.00"), true, null));

        Optional<TeachingUnit> found = teachingUnitDao.findById(unitId);
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("数学课");
        assertThat(found.get().billingMode()).isEqualTo(BillingMode.LESSON_COUNT);
        assertThat(found.get().lessonDurationMinutes()).isEqualTo(60);
        assertThat(found.get().pricePerLesson()).isEqualByComparingTo("50.00");
        assertThat(found.get().active()).isTrue();
    }

    @Test
    void findAllByTeacherIdOnlyReturnsOwnUnitsAcrossBillingModes() {
        Long institutionId = institutionDao.insert("教学单元测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900001002", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900001003", "hash",
                Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "二年级托管班",
                BillingMode.MONTHLY, null, null, true, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "英语课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "三年级托管班",
                BillingMode.MONTHLY, null, null, true, null));

        List<TeachingUnit> found = teachingUnitDao.findAllByTeacherId(teacherAId);

        assertThat(found).hasSize(2);
    }

    @Test
    void findAllByInstitutionIdReturnsAllUnits() {
        Long institutionId = institutionDao.insert("教学单元测试机构D");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900001004", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900001005", "hash",
                Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "语文课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "四年级托管班",
                BillingMode.MONTHLY, null, null, true, null));

        List<TeachingUnit> found = teachingUnitDao.findAllByInstitutionId(institutionId);

        assertThat(found).hasSize(2);
    }

    @Test
    void updateNameAndTeacherChangesBothFields() {
        Long institutionId = institutionDao.insert("教学单元测试机构E");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900001006", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900001007", "hash",
                Role.TEACHER, false, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "四年级托管班",
                BillingMode.MONTHLY, null, null, true, null));

        teachingUnitDao.updateNameAndTeacher(unitId, "五年级托管班", teacherBId);

        TeachingUnit found = teachingUnitDao.findById(unitId).orElseThrow();
        assertThat(found.name()).isEqualTo("五年级托管班");
        assertThat(found.teacherId()).isEqualTo(teacherBId);
    }

    @Test
    void setPriceUpdatesPricePerLesson() {
        Long institutionId = institutionDao.insert("教学单元测试机构F");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020006", "hash",
                Role.TEACHER, false, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "音乐课",
                BillingMode.LESSON_COUNT, 45, null, true, null));

        teachingUnitDao.setPrice(unitId, new BigDecimal("80.00"));

        assertThat(teachingUnitDao.findById(unitId).orElseThrow().pricePerLesson()).isEqualByComparingTo("80.00");
    }

    @Test
    void setActiveTogglesActiveFlag() {
        Long institutionId = institutionDao.insert("教学单元测试机构G");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020007", "hash",
                Role.TEACHER, false, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "体育课",
                BillingMode.LESSON_COUNT, 45, null, true, null));

        teachingUnitDao.setActive(unitId, false);

        assertThat(teachingUnitDao.findById(unitId).orElseThrow().active()).isFalse();
    }

    @Test
    void reassignTeacherChangesOwningTeacher() {
        Long institutionId = institutionDao.insert("教学单元测试机构H");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900020008", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900020009", "hash",
                Role.TEACHER, false, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "书法课",
                BillingMode.LESSON_COUNT, 45, null, true, null));

        teachingUnitDao.reassignTeacher(unitId, teacherBId);

        assertThat(teachingUnitDao.findById(unitId).orElseThrow().teacherId()).isEqualTo(teacherBId);
    }

    @Test
    void reassignAllTeacherMovesAllUnitsFromOldToNewTeacher() {
        Long institutionId = institutionDao.insert("教学单元测试机构I");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900020010", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900020011", "hash",
                Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "舞蹈课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "围棋班",
                BillingMode.MONTHLY, null, null, true, null));

        teachingUnitDao.reassignAllTeacher(teacherAId, teacherBId);

        assertThat(teachingUnitDao.findAllByTeacherId(teacherAId)).isEmpty();
        assertThat(teachingUnitDao.findAllByTeacherId(teacherBId)).hasSize(2);
    }

    @Test
    void deleteByIdRemovesUnit() {
        Long institutionId = institutionDao.insert("教学单元测试机构J");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900020012", "hash",
                Role.TEACHER, false, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "编程课",
                BillingMode.LESSON_COUNT, 45, null, true, null));

        teachingUnitDao.deleteById(unitId);

        assertThat(teachingUnitDao.findById(unitId)).isEmpty();
    }
}
