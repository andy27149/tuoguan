package com.tuoguan.backend.auth.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedServiceTest extends IntegrationTestBase {

    @Autowired
    private SeedService seedService;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Test
    void seedCreatesAdminTeacherWithMustChangePassword() {
        seedService.seed("种子测试机构A", "13800005001", "seed-password");

        Optional<Teacher> found = teacherDao.findByPhone("13800005001");
        assertThat(found).isPresent();
        assertThat(found.get().role()).isEqualTo(Role.ADMIN);
        assertThat(found.get().mustChangePassword()).isTrue();
    }

    @Test
    void seedFailsWhenPhoneAlreadyExists() {
        seedService.seed("种子测试机构B", "13800005002", "seed-password");

        assertThatThrownBy(() -> seedService.seed("种子测试机构C", "13800005002", "another-password"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void seedClassCreatesClassRoomOwnedByTeacher() {
        seedService.seed("种子测试机构D", "13800005003", "seed-password");
        Teacher teacher = teacherDao.findByPhone("13800005003").orElseThrow();

        seedService.seedClass("13800005003", "三年级托管班");

        List<ClassRoom> classRooms = classRoomDao.findAllByTeacherId(teacher.id());
        assertThat(classRooms).hasSize(1);
        assertThat(classRooms.get(0).name()).isEqualTo("三年级托管班");
        assertThat(classRooms.get(0).institutionId()).isEqualTo(teacher.institutionId());
    }

    @Test
    void seedClassFailsWhenTeacherPhoneNotFound() {
        assertThatThrownBy(() -> seedService.seedClass("13800005099", "不存在的班"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void seedPlatformAdminCreatesAccountWithoutInstitution() {
        seedService.seedPlatformAdmin("13800005004", "seed-password");

        Optional<Teacher> found = teacherDao.findByPhone("13800005004");
        assertThat(found).isPresent();
        assertThat(found.get().institutionId()).isNull();
        assertThat(found.get().role()).isEqualTo(Role.PLATFORM_ADMIN);
        assertThat(found.get().mustChangePassword()).isTrue();
    }

    @Test
    void seedPlatformAdminFailsWhenPhoneAlreadyExists() {
        seedService.seedPlatformAdmin("13800005005", "seed-password");

        assertThatThrownBy(() -> seedService.seedPlatformAdmin("13800005005", "another-password"))
                .isInstanceOf(IllegalStateException.class);
    }
}
