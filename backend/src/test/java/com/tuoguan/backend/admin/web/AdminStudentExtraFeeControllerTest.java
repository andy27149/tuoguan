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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStudentExtraFeeControllerTest extends IntegrationTestBase {

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
    void addsAndListsExtraFees() throws Exception {
        Long institutionId = institutionDao.insert("课外费测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900011001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900011002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "课外费一班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班", true, null, null));
        String adminToken = login("13900011001", "admin-password");

        MvcResult createResult = mockMvc.perform(post("/api/admin/students/" + studentId + "/extra-fees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数学课\",\"pricePerLesson\":50.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("数学课"))
                .andExpect(jsonPath("$.pricePerLesson").value(50.00))
                .andReturn();
        Long feeId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/admin/students/" + studentId + "/extra-fees?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("数学课"))
                .andExpect(jsonPath("$[0].pricePerLesson").value(50.00))
                .andExpect(jsonPath("$[0].lessonCount").value(0))
                .andExpect(jsonPath("$[0].amount").value(0));

        mockMvc.perform(put("/api/admin/students/" + studentId + "/extra-fees/" + feeId
                        + "/lesson-count?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonCount\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonCount").value(4))
                .andExpect(jsonPath("$.amount").value(200.00));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/extra-fees?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonCount").value(4))
                .andExpect(jsonPath("$[0].amount").value(200.00));
    }

    @Test
    void rejectsDuplicateExtraFeeName() throws Exception {
        Long institutionId = institutionDao.insert("课外费测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900011003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900011004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "课外费二班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生乙", "一班", true, null, null));
        String adminToken = login("13900011003", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/extra-fees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"英语课\",\"pricePerLesson\":30.00}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/students/" + studentId + "/extra-fees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"英语课\",\"pricePerLesson\":40.00}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deletesExtraFee() throws Exception {
        Long institutionId = institutionDao.insert("课外费测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900011005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900011006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "课外费三班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生丙", "一班", true, null, null));
        String adminToken = login("13900011005", "admin-password");

        MvcResult createResult = mockMvc.perform(post("/api/admin/students/" + studentId + "/extra-fees")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"美术课\",\"pricePerLesson\":20.00}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long feeId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/admin/students/" + studentId + "/extra-fees/" + feeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/extra-fees?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void extraFeesForStudentInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("课外费测试机构D");
        Long institutionBId = institutionDao.insert("课外费测试机构E");
        teacherDao.insert(new Teacher(null, institutionAId, "13900011007",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900011008",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomBId = classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "课外费四班", null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, classRoomBId, "学生丁", "一班", true, null, null));
        String adminToken = login("13900011007", "admin-password");

        mockMvc.perform(get("/api/admin/students/" + studentBId + "/extra-fees?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
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
