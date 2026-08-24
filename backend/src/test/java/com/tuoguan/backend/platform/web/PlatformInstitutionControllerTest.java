package com.tuoguan.backend.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformInstitutionControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void platformAdminCreatesInstitutionAndNewAdminCanLogIn() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003001",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003001", "platform-password");

        MvcResult createResult = mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构A\",\"adminPhone\":\"13600003002\","
                                + "\"adminInitialPassword\":\"initial123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institutionName").value("新开通机构A"))
                .andExpect(jsonPath("$.adminPhone").value("13600003002"))
                .andReturn();
        PlatformInstitutionResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), PlatformInstitutionResponse.class);

        String newAdminToken = login("13600003002", "initial123");
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + newAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"initial123\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/teachers")
                        .header("Authorization", "Bearer " + newAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + created.institutionId() + ")].teacherCount").value(1));
    }

    @Test
    void rejectsDuplicateAdminPhone() throws Exception {
        teacherDao.insert(new Teacher(null, null, "13600003003",
                passwordEncoder.encode("platform-password"), Role.PLATFORM_ADMIN, false, null));
        String platformToken = login("13600003003", "platform-password");

        mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构B\",\"adminPhone\":\"13600003004\","
                                + "\"adminInitialPassword\":\"initial123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构C\",\"adminPhone\":\"13600003004\","
                                + "\"adminInitialPassword\":\"initial123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonPlatformAdminIsForbidden() throws Exception {
        Long institutionId = institutionDao.insert("平台管理测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13600003005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13600003005", "admin-password");

        mockMvc.perform(post("/api/platform/institutions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"institutionName\":\"新开通机构D\",\"adminPhone\":\"13600003006\","
                                + "\"adminInitialPassword\":\"initial123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/institutions")
                        .header("Authorization", "Bearer " + adminToken))
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
