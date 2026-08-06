package com.tuoguan.backend.auth.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedServiceTest extends IntegrationTestBase {

    @Autowired
    private SeedService seedService;

    @Autowired
    private TeacherDao teacherDao;

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
}
