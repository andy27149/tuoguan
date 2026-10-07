package com.tuoguan.backend.kanban.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentMealRecordControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void settingMealTwiceForTheSameDayStaysIdempotent() throws Exception {
        Long institutionId = institutionDao.insert("用餐记录控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900006101",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900006101", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"arrivedAt\":\"08:00\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/meals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(studentId));
    }

    @Test
    void settingMealWithoutArrivalIsRejected() throws Exception {
        Long institutionId = institutionDao.insert("用餐记录控制器测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900006106",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900006106", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/meals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aStudentWithNoMealRecordIsSimplyAbsentFromTheList() throws Exception {
        Long institutionId = institutionDao.insert("用餐记录控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900006102",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班", true, null, null));
        String token = login("13900006102", "password");

        mockMvc.perform(get("/api/classes/" + classRoomId + "/meals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clearingMealRemovesItFromTheList() throws Exception {
        Long institutionId = institutionDao.insert("用餐记录控制器测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900006105",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900006105", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"arrivedAt\":\"08:00\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/students/" + studentId + "/meal?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/meals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void clearingArrivalCascadesToClearTheMealRecord() throws Exception {
        Long institutionId = institutionDao.insert("用餐记录控制器测试机构F");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900006107",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13900006107", "password");

        mockMvc.perform(patch("/api/students/" + studentId + "/arrival")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\",\"arrivedAt\":\"08:00\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/students/" + studentId + "/meal")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/students/" + studentId + "/arrival?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/classes/" + classRoomId + "/meals?date=2026-08-06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teacherCannotOperateOnAnotherTeachersStudentMeal() throws Exception {
        Long institutionId = institutionDao.insert("用餐记录控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900006103",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900006104",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentAId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小明", "三年级2班",
                true, null, null));

        String tokenB = login("13900006104", "password-b");

        mockMvc.perform(patch("/api/students/" + studentAId + "/meal")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-06\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/meals?date=2026-08-06")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/students/" + studentAId + "/meal?date=2026-08-06")
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
