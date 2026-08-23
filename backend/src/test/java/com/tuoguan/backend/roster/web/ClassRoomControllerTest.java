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
    void createsClassAndListsItForOwningTeacher() throws Exception {
        Long institutionId = institutionDao.insert("班级控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800007001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13800007001", "password");

        mockMvc.perform(post("/api/classes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新托管班\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("新托管班"));

        mockMvc.perform(get("/api/classes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("新托管班"));
    }

    @Test
    void rejectsDuplicateClassNameForSameTeacher() throws Exception {
        Long institutionId = institutionDao.insert("班级控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800007002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "重名班", null));
        String token = login("13800007002", "password");

        mockMvc.perform(post("/api/classes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重名班\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherOnlySeesOwnClassesNotAnotherTeachersNewClass() throws Exception {
        Long institutionId = institutionDao.insert("班级控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800007003",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800007004",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        String tokenA = login("13800007003", "password-a");
        String tokenB = login("13800007004", "password-b");

        mockMvc.perform(post("/api/classes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A老师的班\"}"))
                .andExpect(status().isCreated());

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
