package com.tuoguan.backend.unit.web;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTeachingUnitControllerTest extends IntegrationTestBase {

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
    void adminListsBothBillingModesScopedToOwnInstitution() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构A");
        Long otherInstitutionId = institutionDao.insert("教学单元测试机构A2");
        teacherDao.insert(new Teacher(null, institutionId, "13900041001",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一年级1班", BillingMode.MONTHLY,
                null, null, true, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课", BillingMode.LESSON_COUNT,
                45, null, true, null));
        teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, teacherId, "别的机构班", BillingMode.MONTHLY,
                null, null, true, null));
        String token = login("13900041001", "password");

        mockMvc.perform(get("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/admin/teaching-units").queryParam("billingMode", "MONTHLY")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("一年级1班"));
    }

    @Test
    void nonAdminTeacherIsForbiddenFromListingTeachingUnits() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13900041003",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13900041003", "password");

        mockMvc.perform(get("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesClassRoomAndCourseAssigningTeacher() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900041004",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041005",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13900041004", "password");

        mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"MONTHLY\",\"name\":\"新托管班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("新托管班"))
                .andExpect(jsonPath("$.billingMode").value("MONTHLY"))
                .andExpect(jsonPath("$.teacherId").value(teacherId));

        mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"LESSON_COUNT\",\"name\":\"新课外课\",\"teacherId\":" + teacherId
                                + ",\"lessonDurationMinutes\":45,\"pricePerLesson\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("新课外课"))
                .andExpect(jsonPath("$.billingMode").value("LESSON_COUNT"))
                .andExpect(jsonPath("$.lessonDurationMinutes").value(45));
    }

    @Test
    void createRejectsDuplicateNameWithinSameBillingModeAndInstitution() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13900041006",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "重名班", BillingMode.MONTHLY,
                null, null, true, null));
        String token = login("13900041006", "password");

        mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"MONTHLY\",\"name\":\"重名班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createRejectsTeacherFromAnotherInstitution() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构E1");
        Long otherInstitutionId = institutionDao.insert("教学单元测试机构E2");
        teacherDao.insert(new Teacher(null, institutionId, "13900041008",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long outsiderTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13900041009",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13900041008", "password");

        mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"MONTHLY\",\"name\":\"班级\",\"teacherId\":" + outsiderTeacherId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRejectsLessonCountWithoutLessonDurationMinutes() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构F");
        teacherDao.insert(new Teacher(null, institutionId, "13900041010",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13900041010", "password");

        mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"LESSON_COUNT\",\"name\":\"课外课\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsWhenFeatureDisabled() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构G");
        institutionDao.updateFeatureFlags(institutionId, false, true);
        teacherDao.insert(new Teacher(null, institutionId, "13900041012",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041013",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13900041012", "password");

        mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"MONTHLY\",\"name\":\"托管班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromCreatingTeachingUnit() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构H");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041014",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13900041014", "password");

        mockMvc.perform(post("/api/admin/teaching-units")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMode\":\"MONTHLY\",\"name\":\"托管班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchUpdatesNameTeacherPriceAndActiveIndependentlyOrTogether() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构I");
        teacherDao.insert(new Teacher(null, institutionId, "13900041015",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13900041016",
                passwordEncoder.encode("teacher-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13900041017",
                passwordEncoder.encode("teacher-b"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "课外课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13900041015", "password");

        mockMvc.perform(patch("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":120.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePerLesson").value(120.5));

        mockMvc.perform(patch("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + teacherBId + ",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(teacherBId))
                .andExpect(jsonPath("$.active").value(false));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'TEACHING_UNIT_DEACTIVATE' AND target_id = ?",
                Integer.class, courseId);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void patchingTeachingUnitFromAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构J1");
        Long otherInstitutionId = institutionDao.insert("教学单元测试机构J2");
        teacherDao.insert(new Teacher(null, institutionId, "13900041018",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13900041019",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long otherUnitId = teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, otherTeacherId, "别的班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041018", "password");

        mockMvc.perform(patch("/api/admin/teaching-units/" + otherUnitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchRejectsDuplicateNameWithinSameBillingMode() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构K");
        teacherDao.insert(new Teacher(null, institutionId, "13900041020",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041021",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "已存在的班", BillingMode.MONTHLY,
                null, null, true, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "待改名的班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041020", "password");

        mockMvc.perform(patch("/api/admin/teaching-units/" + unitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"已存在的班\",\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromPatchingTeachingUnit() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构L");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041022",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041022", "password");

        mockMvc.perform(patch("/api/admin/teaching-units/" + unitId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletionImpactReturnsStudentCountForBothBillingModes() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构M");
        teacherDao.insert(new Teacher(null, institutionId, "13900041023",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041024",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041023", "password");

        mockMvc.perform(get("/api/admin/teaching-units/" + classRoomId + "/deletion-impact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCount").value(0));
    }

    @Test
    void deletionImpactForTeachingUnitInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构N1");
        Long otherInstitutionId = institutionDao.insert("教学单元测试机构N2");
        teacherDao.insert(new Teacher(null, institutionId, "13900041025",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13900041026",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long otherUnitId = teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, otherTeacherId, "班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041025", "password");

        mockMvc.perform(get("/api/admin/teaching-units/" + otherUnitId + "/deletion-impact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesClassRoomWithoutStudents() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构O");
        teacherDao.insert(new Teacher(null, institutionId, "13900041027",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041028",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041027", "password");

        mockMvc.perform(delete("/api/admin/teaching-units/" + classRoomId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/teaching-units/" + classRoomId + "/deletion-impact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesCourseCascadingEnrollments() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构P");
        teacherDao.insert(new Teacher(null, institutionId, "13900041029",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041030",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13900041029", "password");

        mockMvc.perform(delete("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/teaching-units/" + courseId + "/deletion-impact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingCourseCleansUpRechargeRecordsForDualIdentityStudentsWhoSurvive() throws Exception {
        // 双重身份学生（同时在托管班）删课程时不会被硬删除，之前这里漏了按教学单元清理
        // 充值记录——现在托管学生也能充值了，这条回归要守住，否则会留下指向已删除教学
        // 单元的孤儿充值记录。
        Long institutionId = institutionDao.insert("教学单元测试机构R");
        teacherDao.insert(new Teacher(null, institutionId, "13900041034",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041035",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, new java.math.BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "双重身份生", "一班", true, null, null));
        String adminToken = login("13900041034", "admin-password");

        mockMvc.perform(post("/api/admin/students/" + studentId + "/recharges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseId + ",\"lessonCount\":5,\"note\":\"微信转账\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/teaching-units/" + courseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        Integer rechargeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM course_recharge_record WHERE teaching_unit_id = ?", Integer.class, courseId);
        assertThat(rechargeCount).isZero();
        // 学生本人是托管生，课程删除不应该把人也删了。
        mockMvc.perform(get("/api/admin/students").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + studentId + ")]").exists());
    }

    @Test
    void deleteTeachingUnitFromAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构Q1");
        Long otherInstitutionId = institutionDao.insert("教学单元测试机构Q2");
        teacherDao.insert(new Teacher(null, institutionId, "13900041031",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13900041032",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long otherUnitId = teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, otherTeacherId, "班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041031", "password");

        mockMvc.perform(delete("/api/admin/teaching-units/" + otherUnitId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromDeletingTeachingUnit() throws Exception {
        Long institutionId = institutionDao.insert("教学单元测试机构R");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900041033",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long unitId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13900041033", "password");

        mockMvc.perform(delete("/api/admin/teaching-units/" + unitId)
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
