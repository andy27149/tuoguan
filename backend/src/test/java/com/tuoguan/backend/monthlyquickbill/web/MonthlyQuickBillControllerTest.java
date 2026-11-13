package com.tuoguan.backend.monthlyquickbill.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.StudentUnitEnrollmentDao;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.StudentUnitEnrollment;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonthlyQuickBillControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao teachingUnitDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private StudentUnitEnrollmentDao enrollmentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listStudentsReturnsOnlyEnrolledStudentsWithTheirLessonCountCourses() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构A");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速一班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "羽毛球课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("50.00"), true, null));
        Long enrolledStudentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "一班",
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, enrolledStudentId, courseId, true, null));
        // 已退班的学生不应该出现在列表里
        studentDao.insert(new Student(null, institutionId, classRoomId, "小退", "一班", false, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(get("/api/admin/monthly-quick-bill/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(enrolledStudentId))
                .andExpect(jsonPath("$[0].name").value("小明"))
                .andExpect(jsonPath("$[0].courses.length()").value(1))
                .andExpect(jsonPath("$[0].courses[0].courseId").value(courseId))
                .andExpect(jsonPath("$[0].courses[0].name").value("羽毛球课"));
    }

    @Test
    void submitConvertsAggregateCountsIntoRecordsThatDriveTheExistingBillEngine() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构B");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速二班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小红", "二班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-03\",\"mealDays\":3,\"leaveDays\":2,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2030-03")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealAmount").value(30.00))
                .andExpect(jsonPath("$[0].mealRecordDates.length()").value(3))
                .andExpect(jsonPath("$[0].leaveDays").value(2));
    }

    @Test
    void resubmittingWithDifferentCountsReplacesRatherThanAddsToThePreviousSubmission() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构H");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速六班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小紫", "六班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(put("/api/admin/classes/" + classRoomId + "/billing-rate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tuitionRatePerMonth\":500.00,\"mealRatePerDay\":10.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-07\",\"mealDays\":5,\"leaveDays\":3,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        // 改小一个数字重新提交——不应该在旧记录基础上叠加，账单要跟着新数字变，不是累加。
        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-07\",\"mealDays\":2,\"leaveDays\":1,\"courseConsumptions\":[]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/classes/" + classRoomId + "/bills/generate?month=2030-07")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealAmount").value(20.00))
                .andExpect(jsonPath("$[0].mealRecordDates.length()").value(2))
                .andExpect(jsonPath("$[0].leaveDays").value(1));
    }

    @Test
    void resubmittingCourseConsumptionForTheSameMonthIsRejectedInsteadOfSilentlyMiscounting() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构I");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速七班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "跳绳课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("40.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小橙", "七班",
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-08\",\"mealDays\":0,\"leaveDays\":0,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":2}]}"))
                .andExpect(status().isNoContent());

        // 同一个学生同一门课这个月已经提交过了，再提交应该明确拒绝，而不是悄悄叠加/少算。
        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-08\",\"mealDays\":0,\"leaveDays\":0,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":5}]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "2030-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessonCount").value(2));
    }

    @Test
    void submitRecordsCourseConsumptionForEnrolledCourse() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构C");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速三班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "绘画课",
                BillingMode.LESSON_COUNT, 60, new BigDecimal("80.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小蓝", "三班",
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-04\",\"mealDays\":0,\"leaveDays\":0,"
                                + "\"courseConsumptions\":[{\"courseId\":" + courseId + ",\"count\":4}]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/students/" + studentId + "/course-consumption")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("month", "2030-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseName").value("绘画课"))
                .andExpect(jsonPath("$[0].lessonCount").value(4))
                .andExpect(jsonPath("$[0].amount").value(320.00));
    }

    @Test
    void submitRejectsWhenMealPlusLeaveExceedsWeekdaysInMonth() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构D");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速四班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小绿", "四班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-05\",\"mealDays\":15,\"leaveDays\":15,\"courseConsumptions\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitForStudentInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("快速账单测试机构E");
        Long institutionBId = institutionDao.insert("快速账单测试机构F");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionAId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, uniquePhone(),
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomBId = teachingUnitDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "乙机构班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, classRoomBId, "小乙", "乙班",
                true, null, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/monthly-quick-bill/students/" + studentBId + "/submit")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2030-06\",\"mealDays\":1,\"leaveDays\":0,\"courseConsumptions\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromUsingQuickBill() throws Exception {
        Long institutionId = institutionDao.insert("快速账单测试机构G");
        String teacherPhone = uniquePhone();
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, teacherPhone,
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "快速五班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login(teacherPhone, "teacher-password");

        mockMvc.perform(get("/api/admin/monthly-quick-bill/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + token))
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
