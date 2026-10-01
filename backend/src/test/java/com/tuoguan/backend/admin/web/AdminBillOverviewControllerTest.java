package com.tuoguan.backend.admin.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBillOverviewControllerTest extends IntegrationTestBase {

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
    void getBillReturnsEnrichedDetailIncludingExtraFeeLines() throws Exception {
        Long institutionId = institutionDao.insert("账单详情测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900014001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900014002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "详情一班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班", true, null, null));
        String adminToken = login("13900014001", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        String teacherToken = login("13900014002", "teacher-password");
        MvcResult courseResult = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数学课\",\"lessonDurationMinutes\":60}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(patch("/api/admin/courses/" + courseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":50.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated());

        MvcResult generateResult = mockMvc.perform(post("/api/admin/students/" + studentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long billId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/bills/" + billId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(billId))
                .andExpect(jsonPath("$.studentId").value(studentId))
                .andExpect(jsonPath("$.extraFeeLines.length()").value(1))
                .andExpect(jsonPath("$.extraFeeLines[0].name").value("数学课"));
    }

    @Test
    void listOverviewWithoutMonthShowsAllGeneratedMonthsAndStudentsWithNoBillYet() throws Exception {
        Long institutionId = institutionDao.insert("账单全部月份测试机构");
        teacherDao.insert(new Teacher(null, institutionId, "13900014010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900014011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "全月一班", null));
        Long billedStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "已出账学生", "一班", true, null, null));
        Long unbilledStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "未出账学生", "一班", true, null, null));
        String adminToken = login("13900014010", "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + billedStudentId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/students/" + billedStudentId + "/bills/generate?month=2024-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/bills").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.studentId == " + billedStudentId + " && @.yearMonth == '2024-01')]").exists())
                .andExpect(jsonPath("$[?(@.studentId == " + billedStudentId + " && @.yearMonth == '2024-02')]").exists())
                .andExpect(jsonPath("$[?(@.studentId == " + unbilledStudentId + ")].billId").value(
                        org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void getBillForNonExistentIdReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("账单详情测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900014003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900014003", "admin-password");

        mockMvc.perform(get("/api/admin/bills/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBillForAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("账单详情测试机构C");
        Long institutionBId = institutionDao.insert("账单详情测试机构D");
        teacherDao.insert(new Teacher(null, institutionAId, "13900014004",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900014005",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomBId = classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "详情二班", null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, classRoomBId, "学生乙", "一班", true, null, null));

        teacherDao.insert(new Teacher(null, institutionBId, "13900014006",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String institutionBAdminToken = login("13900014006", "admin-password");
        mockMvc.perform(put("/api/admin/classes/" + classRoomBId + "/billing-rate")
                        .header("Authorization", "Bearer " + institutionBAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":50.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());
        MvcResult generateResult = mockMvc.perform(post("/api/admin/students/" + studentBId + "/bills/generate?month=2024-01")
                        .header("Authorization", "Bearer " + institutionBAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long billId = objectMapper.readTree(generateResult.getResponse().getContentAsString()).get("id").asLong();

        String institutionAAdminToken = login("13900014004", "admin-password");
        mockMvc.perform(get("/api/admin/bills/" + billId)
                        .header("Authorization", "Bearer " + institutionAAdminToken))
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
