package com.tuoguan.backend.course.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStudentAccountControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private TeachingUnitDao courseDao;

    @Autowired
    private CourseConsumptionRecordDao consumptionRecordDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rechargingDualIdentityStudentNowSucceedsAndCoversLaterConsumption() throws Exception {
        // 托管班学生（同时报了课外课）现在也允许预充值：充值之后发生的消课应该被覆盖，
        // 不再全额计入账单——覆盖判定逻辑本来就不区分学生身份，这里验证开放之后确实
        // 按预期工作。
        Long institutionId = institutionDao.insert("充值控制器测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13800013001",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800013002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new TeachingUnit(null, institutionId, teacherId, "一年级1班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小托", "一年级1班", true, null, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        String token = login("13800013001", "password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/recharges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"lessonCount\":2,\"note\":\"微信转账\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lessonCount").value(2));

        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2030, 1, 2), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2030, 1, 3), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2030, 1, 4), new BigDecimal("50.00"), teacherId, null));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2030-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonCount").value(1))
                .andExpect(jsonPath("$[0].coveredByBalanceCount").value(2))
                .andExpect(jsonPath("$[0].amount").value(50.00));
    }

    @Test
    void rechargeAndConsumptionProduceCorrectBalanceIncludingNegative() throws Exception {
        Long institutionId = institutionDao.insert("充值控制器测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13800013003",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800013004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        String token = login("13800013003", "password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/recharges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"lessonCount\":2,\"note\":\"微信转账\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lessonCount").value(2));

        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 2), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 3), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 4), new BigDecimal("50.00"), teacherId, null));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.length()").value(1))
                .andExpect(jsonPath("$.balances[0].lessonsRecharged").value(2))
                .andExpect(jsonPath("$.balances[0].lessonsConsumed").value(3))
                .andExpect(jsonPath("$.balances[0].balance").value(-1))
                .andExpect(jsonPath("$.recharges.length()").value(1))
                .andExpect(jsonPath("$.consumptions.length()").value(3));
    }

    @Test
    void courseConsumptionEndpointSummarizesRecordsByCourseForMonth() throws Exception {
        Long institutionId = institutionDao.insert("充值控制器测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13800013006",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800013007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        String token = login("13800013006", "password");

        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 2), new BigDecimal("50.00"), teacherId, null));
        consumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.of(2024, 1, 3), new BigDecimal("50.00"), teacherId, null));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2024-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("数学课"))
                .andExpect(jsonPath("$[0].lessonCount").value(2))
                .andExpect(jsonPath("$[0].amount").value(100.00));
    }

    @Test
    void teacherRoleCannotCallRechargeOrStatementEndpoints() throws Exception {
        Long institutionId = institutionDao.insert("充值控制器测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800013005",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        String token = login("13800013005", "password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/recharges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"lessonCount\":1,\"note\":null}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // 产品诊断 #48：涉及 institutionId 的接口里，跨机构越权（拿着 A 机构管理员 token
    // 操作 B 机构学生的 id）只覆盖了大约一半，这里补上——充值是直接涉及金钱的操作，
    // 是这类测试里优先级最高的缺口。底层 CourseAccountService 的 requireStudentInInstitution
    // 已经做了校验（见该类源码），这里只是把这个既有防护钉成回归测试，而不是新增防护。
    @Test
    void adminCannotAccessStudentAccountFromAnotherInstitution() throws Exception {
        Long institutionAId = institutionDao.insert("充值控制器测试机构E");
        Long institutionBId = institutionDao.insert("充值控制器测试机构F");
        teacherDao.insert(new Teacher(null, institutionAId, "13800013008",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13800013009",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "数学课",
                BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionBId, null, "小外", null, true, null, null));
        String adminAToken = login("13800013008", "password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/recharges")
                        .header("Authorization", "Bearer " + adminAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"lessonCount\":1,\"note\":null}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-statement")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + adminAToken)
                        .param("month", "2024-01"))
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
