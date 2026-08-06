package com.tuoguan.backend.kanban.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.kanban.domain.ClassDismissal;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.support.IntegrationTestBase;
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
    private ClassRoomDao classRoomDao;

    @Autowired
    private ClassDismissalDao classDismissalDao;

    private Long createClassRoom(String institutionName, String phone) {
        Long institutionId = institutionDao.insert(institutionName);
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, phone, "hash",
                Role.TEACHER, false, null));
        return classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
    }

    @Test
    void insertAndFindByClassRoomIdAndDateRoundTrips() {
        Long classRoomId = createClassRoom("放学测试机构A", "13900004001");
        Long institutionId = classRoomDao.findById(classRoomId).orElseThrow().institutionId();

        classDismissalDao.insert(new ClassDismissal(null, institutionId, classRoomId,
                LocalDate.of(2026, 8, 6), null));

        Optional<ClassDismissal> found = classDismissalDao.findByClassRoomIdAndDate(
                classRoomId, LocalDate.of(2026, 8, 6));
        assertThat(found).isPresent();

        assertThat(classDismissalDao.findByClassRoomIdAndDate(classRoomId, LocalDate.of(2026, 8, 5)))
                .isEmpty();
    }

    @Test
    void deleteByClassRoomIdAndDateRemovesRow() {
        Long classRoomId = createClassRoom("放学测试机构B", "13900004002");
        Long institutionId = classRoomDao.findById(classRoomId).orElseThrow().institutionId();
        classDismissalDao.insert(new ClassDismissal(null, institutionId, classRoomId,
                LocalDate.of(2026, 8, 6), null));

        classDismissalDao.deleteByClassRoomIdAndDate(classRoomId, LocalDate.of(2026, 8, 6));

        assertThat(classDismissalDao.findByClassRoomIdAndDate(classRoomId, LocalDate.of(2026, 8, 6)))
                .isEmpty();
    }
}
