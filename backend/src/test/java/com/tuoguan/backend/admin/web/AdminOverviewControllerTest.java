package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.course.dao.CourseConsumptionRecordDao;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseConsumptionRecord;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOverviewControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private CourseRechargeRecordDao courseRechargeRecordDao;

    @Autowired
    private CourseConsumptionRecordDao courseConsumptionRecordDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void lowBalanceIncludesStudentBelowThresholdAndExcludesStudentAtOrAboveIt() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900015001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long lowBalanceStudentId = studentDao.insert(
                new Student(null, institutionId, null, "低余额学生", null, true, null, null));
        Long healthyStudentId = studentDao.insert(
                new Student(null, institutionId, null, "余额充足学生", null, true, null, null));
        // 低余额学生：充值 2，消课 0，余额 2（< 3，应该出现）。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, lowBalanceStudentId, courseId,
                2, null, teacherId, null));
        // 余额充足学生：充值 5，消课 0，余额 5（>= 3，不应该出现）。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, healthyStudentId, courseId,
                5, null, teacherId, null));
        String adminToken = login("13900015001", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + lowBalanceStudentId + ")].balance").value(
                        org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.studentId == " + healthyStudentId + ")]").isEmpty());
    }

    @Test
    void lowBalanceIncludesZeroAndNegativeBalances() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900015010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "欠费学生", null, true, null, null));
        // 充值 1，消课 2，余额 -1。
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId,
                1, null, teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.now(), BigDecimal.valueOf(50), teacherId, null));
        courseConsumptionRecordDao.insert(new CourseConsumptionRecord(null, institutionId, studentId, courseId,
                LocalDate.now().minusDays(1), BigDecimal.valueOf(50), teacherId, null));
        String adminToken = login("13900015010", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == " + studentId + ")].balance").value(
                        org.hamcrest.Matchers.contains(-1)));
    }

    @Test
    void lowBalanceListsTwoCoursesForTheSameStudentAsSeparateRows() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900015020",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900015021",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "语文课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "双课程学生", null, true, null, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseAId,
                1, null, teacherId, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseBId,
                2, null, teacherId, null));
        String adminToken = login("13900015020", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.courseId == " + courseAId + ")].balance").value(
                        org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.courseId == " + courseBId + ")].balance").value(
                        org.hamcrest.Matchers.contains(2)));
    }

    @Test
    void lowBalanceReturnsEmptyListForInstitutionWithNoRecords() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900015030",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13900015030", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void lowBalanceNeverLeaksAnotherInstitutionsStudents() throws Exception {
        Long institutionAId = institutionDao.insert("总览课时预警测试机构E");
        Long institutionBId = institutionDao.insert("总览课时预警测试机构F");
        teacherDao.insert(new Teacher(null, institutionAId, "13900015040",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13900015041",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "B机构课程",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentBId = studentDao.insert(
                new Student(null, institutionBId, null, "B机构低余额学生", null, true, null, null));
        courseRechargeRecordDao.insert(new CourseRechargeRecord(null, institutionBId, studentBId, courseBId,
                1, null, teacherBId, null));
        String adminAToken = login("13900015040", "admin-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + adminAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void nonAdminIsForbiddenFromLowBalance() throws Exception {
        Long institutionId = institutionDao.insert("总览课时预警测试机构G");
        teacherDao.insert(new Teacher(null, institutionId, "13900015050",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String teacherToken = login("13900015050", "teacher-password");

        mockMvc.perform(get("/api/admin/overview/low-balance")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
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
