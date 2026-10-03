package com.tuoguan.backend.roster.web;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClassRoomControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void teacherListsOwnClasses() throws Exception {
        Long institutionId = institutionDao.insert("班级控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800007001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "新托管班", BillingMode.MONTHLY,
                null, null, true, null));
        String token = login("13800007001", "password");

        mockMvc.perform(get("/api/classes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("新托管班"));
    }

    @Test
    void teacherOnlySeesOwnClassesNotAnotherTeachersClass() throws Exception {
        Long institutionId = institutionDao.insert("班级控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800007003",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800007004",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A老师的班", BillingMode.MONTHLY,
                null, null, true, null));
        String tokenB = login("13800007004", "password-b");

        mockMvc.perform(get("/api/classes")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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
