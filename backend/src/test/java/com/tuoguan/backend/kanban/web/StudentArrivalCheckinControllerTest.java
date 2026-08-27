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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentArrivalCheckinControllerTest extends IntegrationTestBase {

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
    void settingArrivalTwiceOverwritesThePreviousRecord() throws Exception {
        Long institutionId = institutionDao.insert("到达签到控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900009101",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900009101", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"arrivedAt\":\"15:40\"}"))
                .andExpect(status().isNoContent());

        MvcResult listResult = mockMvc.perform(get("/api/classes/" + classRoomId + "/arrivals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        List<StudentArrivalCheckinResponse> arrivals = objectMapper.readValue(
                listResult.getResponse().getContentAsString(StandardCharsets.UTF_8),
                objectMapper.getTypeFactory().constructCollectionType(List.class, StudentArrivalCheckinResponse.class));
        assertThat(arrivals.get(0).studentId()).isEqualTo(studentId);
        assertThat(arrivals.get(0).arrivedAt()).isEqualTo("15:40");

        mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"arrivedAt\":\"16:05\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/arrivals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].arrivedAt").value("16:05"));
    }

    @Test
    void aStudentWithNoArrivalIsSimplyAbsentFromTheList() throws Exception {
        Long institutionId = institutionDao.insert("到达签到控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900009102",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null, null));
        String token = login("13900009102", "password");

        mockMvc.perform(get("/api/classes/" + classRoomId + "/arrivals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clearingArrivalRemovesItFromTheList() throws Exception {
        Long institutionId = institutionDao.insert("到达签到控制器测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900009105",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900009105", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"arrivedAt\":\"15:40\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/students/" + studentId + "/arrival?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/arrivals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teacherCannotOperateOnAnotherTeachersStudentArrival() throws Exception {
        Long institutionId = institutionDao.insert("到达签到控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900009103",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900009104",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小明", "三年级2班",
                true, null, null));

        String tokenB = login("13900009104", "password-b");

        mockMvc.perform(patch("/api/students/" + studentAId + "/arrival")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"arrivedAt\":\"17:00\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/arrivals?date=2026-08-06")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/students/" + studentAId + "/arrival?date=2026-08-06")
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
