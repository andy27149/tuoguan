package com.tuoguan.backend.kanban.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentPickupCheckinControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void settingPickupTwiceOverwritesThePreviousRecord() throws Exception {
        Long institutionId = institutionDao.insert("接送签到控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900009101",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900009101", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/pickup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"pickedUpBy\":\"奶奶\",\"pickedUpAt\":\"17:30\"}"))
                .andExpect(status().isNoContent());

        MvcResult listResult = mockMvc.perform(get("/api/classes/" + classRoomId + "/pickups?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        List<StudentPickupCheckinResponse> pickups = objectMapper.readValue(
                listResult.getResponse().getContentAsString(StandardCharsets.UTF_8),
                objectMapper.getTypeFactory().constructCollectionType(List.class, StudentPickupCheckinResponse.class));
        assertThat(pickups.get(0).studentId()).isEqualTo(studentId);
        assertThat(pickups.get(0).pickedUpBy()).isEqualTo("奶奶");
        assertThat(pickups.get(0).pickedUpAt()).isEqualTo("17:30");

        mockMvc.perform(patch("/api/students/" + studentId + "/pickup")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"pickedUpBy\":\"爸爸\",\"pickedUpAt\":\"18:00\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/pickups?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pickedUpBy").value("爸爸"))
                .andExpect(jsonPath("$[0].pickedUpAt").value("18:00"));
    }

    @Test
    void aStudentWithNoPickupIsSimplyAbsentFromTheList() throws Exception {
        Long institutionId = institutionDao.insert("接送签到控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900009102",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null, null));
        String token = login("13900009102", "password");

        mockMvc.perform(get("/api/classes/" + classRoomId + "/pickups?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teacherCannotOperateOnAnotherTeachersStudentPickup() throws Exception {
        Long institutionId = institutionDao.insert("接送签到控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900009103",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900009104",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小明", "三年级2班",
                true, null, null));

        String tokenB = login("13900009104", "password-b");

        mockMvc.perform(patch("/api/students/" + studentAId + "/pickup")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"pickedUpBy\":\"陌生人\",\"pickedUpAt\":\"17:00\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/pickups?date=2026-08-06")
                        .header("Authorization", "Bearer " + tokenB))
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
