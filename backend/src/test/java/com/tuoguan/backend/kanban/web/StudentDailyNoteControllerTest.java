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

class StudentDailyNoteControllerTest extends IntegrationTestBase {

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
    void settingRatingAndCommentIndependentlyMergesIntoOneNote() throws Exception {
        Long institutionId = institutionDao.insert("每日评价控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900007001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900007001", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/rating")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"rating\":4}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/students/" + studentId + "/comment")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"comment\":\"今天表现很棒\"}"))
                .andExpect(status().isNoContent());

        MvcResult listResult = mockMvc.perform(get("/api/classes/" + classRoomId + "/student-notes?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        List<StudentDailyNoteResponse> notes = objectMapper.readValue(
                listResult.getResponse().getContentAsString(StandardCharsets.UTF_8),
                objectMapper.getTypeFactory().constructCollectionType(List.class, StudentDailyNoteResponse.class));
        assertThat(notes.get(0).studentId()).isEqualTo(studentId);
        assertThat(notes.get(0).rating()).isEqualTo(4);
        assertThat(notes.get(0).comment()).isEqualTo("今天表现很棒");

        mockMvc.perform(patch("/api/students/" + studentId + "/rating")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"rating\":5}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/student-notes?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].comment").value("今天表现很棒"));
    }

    @Test
    void aStudentWithNoNoteIsSimplyAbsentFromTheList() throws Exception {
        Long institutionId = institutionDao.insert("每日评价控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900007002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管班", null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null, null));
        String token = login("13900007002", "password");

        mockMvc.perform(get("/api/classes/" + classRoomId + "/student-notes?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teacherCannotOperateOnAnotherTeachersStudentNotes() throws Exception {
        Long institutionId = institutionDao.insert("每日评价控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900007003",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900007004",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherAId, "A班", null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小明", "三年级2班",
                true, null, null));

        String tokenB = login("13900007004", "password-b");

        mockMvc.perform(patch("/api/students/" + studentAId + "/rating")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"rating\":3}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/student-notes?date=2026-08-06")
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
