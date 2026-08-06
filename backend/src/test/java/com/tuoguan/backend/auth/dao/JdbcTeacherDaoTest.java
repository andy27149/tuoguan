package com.tuoguan.backend.auth.dao;

import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTeacherDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Test
    void insertAndFindByPhoneRoundTrips() {
        Long institutionId = institutionDao.insert("测试机构B");
        Teacher teacher = new Teacher(null, institutionId, "13900000001", "hashed-password",
                Role.TEACHER, true, null);

        Long teacherId = teacherDao.insert(teacher);

        Optional<Teacher> found = teacherDao.findByPhone("13900000001");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(teacherId);
        assertThat(found.get().institutionId()).isEqualTo(institutionId);
        assertThat(found.get().role()).isEqualTo(Role.TEACHER);
        assertThat(found.get().mustChangePassword()).isTrue();
    }

    @Test
    void findByIdRoundTrips() {
        Long institutionId = institutionDao.insert("测试机构C");
        Teacher teacher = new Teacher(null, institutionId, "13900000002", "hashed-password",
                Role.ADMIN, false, null);
        Long teacherId = teacherDao.insert(teacher);

        Optional<Teacher> found = teacherDao.findById(teacherId);

        assertThat(found).isPresent();
        assertThat(found.get().phone()).isEqualTo("13900000002");
    }

    @Test
    void findByPhoneReturnsEmptyWhenNotFound() {
        Optional<Teacher> found = teacherDao.findByPhone("13900000099");

        assertThat(found).isEmpty();
    }

    @Test
    void updatePasswordChangesHashAndClearsMustChangeFlag() {
        Long institutionId = institutionDao.insert("测试机构D");
        Teacher teacher = new Teacher(null, institutionId, "13900000003", "old-hash",
                Role.TEACHER, true, null);
        Long teacherId = teacherDao.insert(teacher);

        teacherDao.updatePassword(teacherId, "new-hash");

        Optional<Teacher> found = teacherDao.findById(teacherId);
        assertThat(found).isPresent();
        assertThat(found.get().passwordHash()).isEqualTo("new-hash");
        assertThat(found.get().mustChangePassword()).isFalse();
    }
}
