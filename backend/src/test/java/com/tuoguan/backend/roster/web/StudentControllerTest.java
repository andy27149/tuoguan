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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentControllerTest extends IntegrationTestBase {

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
    void createListAndUpdateStudentInOwnClass() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800008001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        String token = login("13800008001", "password");

        MvcResult createResult = mockMvc.perform(post("/api/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小明\",\"schoolClassName\":\"三年级2班\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("小明"))
                .andExpect(jsonPath("$.enrolled").value(true))
                .andReturn();
        StudentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), StudentResponse.class);

        mockMvc.perform(get("/api/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(put("/api/students/" + created.id())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小明明\",\"schoolClassName\":\"三年级3班\",\"enrolled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("小明明"))
                .andExpect(jsonPath("$.schoolClassName").value("三年级3班"))
                .andExpect(jsonPath("$.enrolled").value(false));
    }

    @Test
    void teacherCannotAccessAnotherTeachersClassOrStudents() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构B");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800008002",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800008003",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));

        String tokenB = login("13800008003", "password-b");

        mockMvc.perform(post("/api/classes/" + classRoomAId + "/students")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小红\",\"schoolClassName\":\"四年级1班\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/students")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCannotUpdateAnotherTeachersStudent() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800008004",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800008005",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));

        String tokenA = login("13800008004", "password-a");
        String tokenB = login("13800008005", "password-b");

        MvcResult createResult = mockMvc.perform(post("/api/classes/" + classRoomAId + "/students")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小刚\",\"schoolClassName\":\"五年级1班\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        StudentResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), StudentResponse.class);

        mockMvc.perform(put("/api/students/" + created.id())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\",\"schoolClassName\":\"五年级2班\",\"enrolled\":true}"))
                .andExpect(status().isNotFound());
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
