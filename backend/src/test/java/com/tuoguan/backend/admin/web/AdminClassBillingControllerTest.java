package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.billing.dao.ClassBillingRateDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassBillingControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private ClassBillingRateDao classBillingRateDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void billingRateNotConfiguredReturnsNull() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900010001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费一班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900010001", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }

    @Test
    void upsertBillingRateThenReadItBack() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900010003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费二班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900010003", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionRatePerMonth").value(50.00))
                .andExpect(jsonPath("$.mealRatePerDay").value(10.00));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionRatePerMonth").value(50.00));

        // upsert again should update, not duplicate
        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":60.00,\"mealRatePerDay\":12.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tuitionRatePerMonth").value(60.00));
        assertThat(classBillingRateDao.findByTeachingUnitId(classRoomId)).get()
                .extracting(r -> r.mealRatePerDay().doubleValue())
                .isEqualTo(12.00);
    }

    @Test
    void billingRateForClassRoomInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("班级计费测试机构C");
        Long institutionBId = institutionDao.insert("班级计费测试机构D");
        teacherDao.insert(new Teacher(null, institutionAId, "13900010005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900010006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "计费三班",
                BillingMode.MONTHLY, null, null, true, null));
        String adminToken = login("13900010005", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromReadingBillingRate() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费四班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900010007", "teacher-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void generateBillsForClassSkipsUnenrolledStudentsAndFailsWithoutRate() throws Exception {
        Long institutionId = institutionDao.insert("班级计费测试机构G");
        teacherDao.insert(new Teacher(null, institutionId, "13900010010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900010011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "计费六班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "学生丁", "一班", true, null, null));
        String adminToken = login("13900010010", "admin-password");

        // no billing rate configured yet
        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        studentDao.insert(new Student(null, institutionId, classRoomId, "学生戊", "二班", false, null, null));

        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/bills?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
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
