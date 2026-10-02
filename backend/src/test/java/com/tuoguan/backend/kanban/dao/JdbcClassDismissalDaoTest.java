package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.kanban.domain.ClassDismissal;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcClassDismissalDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private ClassDismissalDao classDismissalDao;

    private Long createClassRoom(String institutionName, String phone) {
        Long institutionId = institutionDao.insert(institutionName);
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, phone, "hash",
                Role.TEACHER, false, null));
        return teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
    }

    @Test
    void insertAndFindByTeachingUnitIdAndDateRoundTrips() {
        Long classRoomId = createClassRoom("放学测试机构A", "13900004001");
        Long institutionId = teachingUnitDao.findById(classRoomId).orElseThrow().institutionId();

        classDismissalDao.insert(new ClassDismissal(null, institutionId, classRoomId,
                LocalDate.of(2026, 8, 6), null));

        Optional<ClassDismissal> found = classDismissalDao.findByTeachingUnitIdAndDate(
                classRoomId, LocalDate.of(2026, 8, 6));
        assertThat(found).isPresent();

        assertThat(classDismissalDao.findByTeachingUnitIdAndDate(classRoomId, LocalDate.of(2026, 8, 5)))
                .isEmpty();
    }

    @Test
    void deleteByTeachingUnitIdAndDateRemovesRow() {
        Long classRoomId = createClassRoom("放学测试机构B", "13900004002");
        Long institutionId = teachingUnitDao.findById(classRoomId).orElseThrow().institutionId();
        classDismissalDao.insert(new ClassDismissal(null, institutionId, classRoomId,
                LocalDate.of(2026, 8, 6), null));

        classDismissalDao.deleteByTeachingUnitIdAndDate(classRoomId, LocalDate.of(2026, 8, 6));

        assertThat(classDismissalDao.findByTeachingUnitIdAndDate(classRoomId, LocalDate.of(2026, 8, 6)))
                .isEmpty();
    }
}
