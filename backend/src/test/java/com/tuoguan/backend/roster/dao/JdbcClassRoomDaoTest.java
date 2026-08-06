package com.tuoguan.backend.roster.dao;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcClassRoomDaoTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Test
    void insertAndFindByIdRoundTrips() {
        Long institutionId = institutionDao.insert("班级测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900001001", "hash",
                Role.TEACHER, false, null));

        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "一年级托管班", null));

        Optional<ClassRoom> found = classRoomDao.findById(classRoomId);
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("一年级托管班");
        assertThat(found.get().teacherId()).isEqualTo(teacherId);
    }

    @Test
    void findAllByTeacherIdOnlyReturnsOwnClasses() {
        Long institutionId = institutionDao.insert("班级测试机构B");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900001002", "hash",
                Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900001003", "hash",
                Role.TEACHER, false, null));
        classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "二年级托管班", null));
        classRoomDao.insert(new ClassRoom(null, institutionId, teacherBId, "三年级托管班", null));

        List<ClassRoom> found = classRoomDao.findAllByTeacherId(teacherAId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("二年级托管班");
    }
}
