package com.tuoguan.backend.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherControllerIsolationTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teachersFromDifferentInstitutionsCannotSeeEachOthersData() throws Exception {
        Long institutionAId = institutionDao.insert("隔离测试机构A");
        Teacher teacherA = new Teacher(null, institutionAId, "13800002001",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null);
        teacherDao.insert(teacherA);

        Long institutionBId = institutionDao.insert("隔离测试机构B");
        Teacher teacherB = new Teacher(null, institutionBId, "13800002002",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null);
        teacherDao.insert(teacherB);

        String tokenA = login("13800002001", "password-a");
        String tokenB = login("13800002002", "password-b");

        mockMvc.perform(get("/api/teachers/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("13800002001"))
                .andExpect(jsonPath("$.institutionId").value(institutionAId));

        mockMvc.perform(get("/api/teachers/me").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("13800002002"))
                .andExpect(jsonPath("$.institutionId").value(institutionBId));
    }

    @Test
    void meEndpointRejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/teachers/me"))
                .andExpect(status().isUnauthorized());
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
