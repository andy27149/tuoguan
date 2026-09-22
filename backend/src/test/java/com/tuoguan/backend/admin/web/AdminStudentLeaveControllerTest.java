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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStudentLeaveControllerTest extends IntegrationTestBase {

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
    void registersSingleDayLeaveAndListsByMonth() throws Exception {
        Long institutionId = institutionDao.insert("请假测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900012001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900012002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "请假一班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生甲", "一班", true, null, null));
        String adminToken = login("13900012001", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/leave-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2024-01-10\",\"endDate\":\"2024-01-10\",\"reason\":\"感冒\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/leave-records?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].leaveDate").value("2024-01-10"));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/leave-records?month=2024-02")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void registersMultiDayRangeAndIsIdempotent() throws Exception {
        Long institutionId = institutionDao.insert("请假测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900012003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900012004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "请假二班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生乙", "一班", true, null, null));
        String adminToken = login("13900012003", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/leave-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2024-01-08\",\"endDate\":\"2024-01-10\",\"reason\":\"发烧\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // repeated registration of an overlapping day should stay idempotent, not duplicate
        mockMvc.perform(post("/api/admin/students/" + studentId + "/leave-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2024-01-10\",\"endDate\":\"2024-01-10\",\"reason\":\"发烧\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/leave-records?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void cancelsLeaveForASpecificDate() throws Exception {
        Long institutionId = institutionDao.insert("请假测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900012005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900012006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "请假三班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生丙", "一班", true, null, null));
        String adminToken = login("13900012005", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/leave-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2024-01-15\",\"endDate\":\"2024-01-15\",\"reason\":\"事假\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/students/" + studentId + "/leave-records/2024-01-15")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/leave-records?month=2024-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidDateRangeReturnsBadRequest() throws Exception {
        Long institutionId = institutionDao.insert("请假测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900012007",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900012008",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "请假四班", null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "学生丁", "一班", true, null, null));
        String adminToken = login("13900012007", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/leave-records")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2024-01-10\",\"endDate\":\"2024-01-01\",\"reason\":\"跨月\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leaveRecordsForStudentInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("请假测试机构E");
        Long institutionBId = institutionDao.insert("请假测试机构F");
        teacherDao.insert(new Teacher(null, institutionAId, "13900012009",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900012010",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomBId = classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "请假五班", null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, classRoomBId, "学生戊", "一班", true, null, null));
        String adminToken = login("13900012009", "admin-password");

        mockMvc.perform(get("/api/admin/students/" + studentBId + "/leave-records?month=2024-01")
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
