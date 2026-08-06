package com.tuoguan.backend.roster.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
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

class ClassRoomControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listOnlyReturnsClassesOwnedByCurrentTeacher() throws Exception {
        Long institutionId = institutionDao.insert("班级控制器测试机构A");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800007001",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800007002",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "一年级托管班", null));
        classRoomDao.insert(new ClassRoom(null, institutionId, teacherBId, "二年级托管班", null));

        String tokenA = login("13800007001", "password-a");

        mockMvc.perform(get("/api/classes").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("一年级托管班"));
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
