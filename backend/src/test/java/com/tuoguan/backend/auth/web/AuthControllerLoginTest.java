package com.tuoguan.backend.auth.web;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerLoginTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginSucceedsWithValidCredentials() throws Exception {
        Long institutionId = institutionDao.insert("登录测试机构A");
        Teacher teacher = new Teacher(null, institutionId, "13800001001",
                passwordEncoder.encode("correct-password"), Role.TEACHER, true, null);
        teacherDao.insert(teacher);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800001001\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        Long institutionId = institutionDao.insert("登录测试机构B");
        Teacher teacher = new Teacher(null, institutionId, "13800001002",
                passwordEncoder.encode("correct-password"), Role.TEACHER, false, null);
        teacherDao.insert(teacher);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800001002\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailsWhenPhoneDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800001099\",\"password\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());
    }
}
