package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Institution;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminInstitutionControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminDisablesCustodyAndKeepsOffCampusEnabled() throws Exception {
        Long institutionId = institutionDao.insert("机构功能开关测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13700005001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String token = login("13700005001", "admin-password");

        mockMvc.perform(put("/api/admin/institution/feature-flags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custodyEnabled\":false,\"offCampusEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custodyEnabled").value(false))
                .andExpect(jsonPath("$.offCampusEnabled").value(true));

        Institution updated = institutionDao.findById(institutionId).orElseThrow();
        assertThat(updated.custodyEnabled()).isFalse();
        assertThat(updated.offCampusEnabled()).isTrue();
    }

    @Test
    void rejectsDisablingBothFeatureFlags() throws Exception {
        Long institutionId = institutionDao.insert("机构功能开关测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13700005002",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String token = login("13700005002", "admin-password");

        mockMvc.perform(put("/api/admin/institution/feature-flags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custodyEnabled\":false,\"offCampusEnabled\":false}"))
                .andExpect(status().isBadRequest());

        Institution unchanged = institutionDao.findById(institutionId).orElseThrow();
        assertThat(unchanged.custodyEnabled()).isTrue();
        assertThat(unchanged.offCampusEnabled()).isTrue();
    }

    @Test
    void nonAdminTeacherIsForbiddenFromUpdatingFeatureFlags() throws Exception {
        Long institutionId = institutionDao.insert("机构功能开关测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13700005003",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13700005003", "teacher-password");

        mockMvc.perform(put("/api/admin/institution/feature-flags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"custodyEnabled\":false,\"offCampusEnabled\":true}"))
                .andExpect(status().isForbidden());
    }

    private String login(String phone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        LoginResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);
        return response.token();
    }
}
