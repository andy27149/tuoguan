package com.tuoguan.backend.admin.web;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStudentControllerTest extends IntegrationTestBase {

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
    void listsMixedClassRoomAndOffCampusStudentsWithCorrectFlag() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13800012001",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "一年级1班",
                BillingMode.MONTHLY, null, null, true, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小托", "一年级1班", true, null, null));
        studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        String token = login("13800012001", "password");

        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name=='小托')].offCampusOnly").value(false))
                .andExpect(jsonPath("$[?(@.name=='小外')].offCampusOnly").value(true));
    }

    @Test
    void doesNotLeakStudentsFromOtherInstitutions() throws Exception {
        Long institutionAId = institutionDao.insert("管理端学生测试机构B1");
        Long institutionBId = institutionDao.insert("管理端学生测试机构B2");
        teacherDao.insert(new Teacher(null, institutionAId, "13800012003",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        studentDao.insert(new Student(null, institutionBId, null, "别人机构的学生", null, true, null, null));
        String token = login("13800012003", "password");

        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void teacherRoleCannotCallAdminStudentEndpoint() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13800012004",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13800012004", "password");

        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesPureOffCampusStudentWithoutTeachingUnit() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13800012005",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        String token = login("13800012005", "password");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"纯课外课学生\",\"schoolClassName\":\"四年级1班\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("纯课外课学生"))
                .andExpect(jsonPath("$.classRoomId").doesNotExist())
                .andExpect(jsonPath("$.offCampusOnly").value(true));
    }

    @Test
    void adminCreatesStudentAssignedToMonthlyTeachingUnit() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构E");
        teacherDao.insert(new Teacher(null, institutionId, "13800012006",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "二年级1班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13800012006", "password");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管学生\",\"schoolClassName\":\"二年级1班\",\"teachingUnitId\":"
                                + classRoomId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("托管学生"))
                .andExpect(jsonPath("$.classRoomId").value(classRoomId))
                .andExpect(jsonPath("$.offCampusOnly").value(false));
    }

    @Test
    void adminCreateStudentRejectsTeachingUnitFromAnotherInstitution() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构F1");
        Long otherInstitutionId = institutionDao.insert("管理端学生测试机构F2");
        teacherDao.insert(new Teacher(null, institutionId, "13800012008",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13800012009",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long otherClassRoomId = teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, otherTeacherId,
                "别的机构的班", BillingMode.MONTHLY, null, null, true, null));
        String token = login("13800012008", "password");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\",\"teachingUnitId\":" + otherClassRoomId + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreateStudentRejectsLessonCountTeachingUnitAsAssignment() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构G");
        teacherDao.insert(new Teacher(null, institutionId, "13800012010",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "课外课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13800012010", "password");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\",\"teachingUnitId\":" + courseId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCreatesStudentEnrolledInOffCampusCourses() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构G2");
        teacherDao.insert(new Teacher(null, institutionId, "13800012028",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012029",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "跆拳道课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13800012028", "password");

        MvcResult result = mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"报课学生\",\"courseIds\":[" + courseId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolledCourseIds[0]").value(courseId))
                .andReturn();
        Long studentId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        List<StudentUnitEnrollment> enrollments = enrollmentDao.findAllByStudentId(studentId);
        assertThat(enrollments).hasSize(1);
        assertThat(enrollments.get(0).teachingUnitId()).isEqualTo(courseId);
        assertThat(enrollments.get(0).active()).isTrue();
    }

    @Test
    void adminCreateStudentRejectsCourseIdFromAnotherInstitution() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构G3A");
        Long otherInstitutionId = institutionDao.insert("管理端学生测试机构G3B");
        teacherDao.insert(new Teacher(null, institutionId, "13800012030",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13800012031",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long otherCourseId = teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, otherTeacherId,
                "别的机构的课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13800012030", "password");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\",\"courseIds\":[" + otherCourseId + "]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreateStudentRejectsMonthlyUnitAsCourseId() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构G4");
        teacherDao.insert(new Teacher(null, institutionId, "13800012032",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012033",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        String token = login("13800012032", "password");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\",\"courseIds\":[" + classRoomId + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromCreatingStudent() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构H");
        teacherDao.insert(new Teacher(null, institutionId, "13800012012",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13800012012", "password");

        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRenamesAndChangesSchoolClassName() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构I");
        teacherDao.insert(new Teacher(null, institutionId, "13800012013",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "旧名字", "旧学籍班", true, null, null));
        String token = login("13800012013", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\",\"schoolClassName\":\"新学籍班\",\"enrolled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新名字"))
                .andExpect(jsonPath("$.schoolClassName").value("新学籍班"));
    }

    @Test
    void adminTransfersStudentBetweenTeachingUnits() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构J");
        teacherDao.insert(new Teacher(null, institutionId, "13800012014",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012015",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long oldClassRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "旧托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long newClassRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "新托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, oldClassRoomId, "转班生", "一班", true, null, null));
        String token = login("13800012014", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"转班生\",\"schoolClassName\":\"一班\",\"teachingUnitId\":" + newClassRoomId
                                + ",\"enrolled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classRoomId").value(newClassRoomId))
                .andExpect(jsonPath("$.classRoomName").value("新托管班"));
    }

    @Test
    void adminTransfersCustodyStudentToPureOffCampus() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构K");
        teacherDao.insert(new Teacher(null, institutionId, "13800012016",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012017",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "退托管生", "一班", true, null, null));
        String token = login("13800012016", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"退托管生\",\"schoolClassName\":\"一班\",\"enrolled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classRoomId").doesNotExist())
                .andExpect(jsonPath("$.offCampusOnly").value(true));
    }

    @Test
    void adminUpdateSyncsCourseEnrollmentsAddingAndRemoving() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构L0");
        teacherDao.insert(new Teacher(null, institutionId, "13800012034",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012035",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        Long courseBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "报课生", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseAId, true, null));
        String token = login("13800012034", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"报课生\",\"enrolled\":true,\"courseIds\":[" + courseBId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledCourseIds[0]").value(courseBId))
                .andExpect(jsonPath("$.enrolledCourseIds.length()").value(1));

        List<StudentUnitEnrollment> enrollments = enrollmentDao.findAllByStudentId(studentId);
        StudentUnitEnrollment courseA = enrollments.stream()
                .filter(e -> e.teachingUnitId().equals(courseAId)).findFirst().orElseThrow();
        StudentUnitEnrollment courseB = enrollments.stream()
                .filter(e -> e.teachingUnitId().equals(courseBId)).findFirst().orElseThrow();
        assertThat(courseA.active()).isFalse();
        assertThat(courseB.active()).isTrue();
    }

    @Test
    void adminUpdatePreservesEnrollmentOnDeactivatedCourseNotOfferedInCourseIds() throws Exception {
        // 已停用的课外课不会出现在管理员编辑表单的勾选列表里，所以提交的 courseIds
        // 自然不会包含它——这种情况下不能因为它"不在列表里"就被误判为要取消报名。
        Long institutionId = institutionDao.insert("管理端学生测试机构L0B");
        teacherDao.insert(new Teacher(null, institutionId, "13800012036",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012037",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long retiredCourseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "已停用的课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "老报课生", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, retiredCourseId, true, null));
        teachingUnitDao.setActive(retiredCourseId, false);
        String token = login("13800012036", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"老报课生\",\"enrolled\":true,\"courseIds\":[]}"))
                .andExpect(status().isOk());

        StudentUnitEnrollment enrollment = enrollmentDao.findAllByStudentId(studentId).get(0);
        assertThat(enrollment.active()).isTrue();
    }

    @Test
    void adminUpdateRejectsTeachingUnitFromAnotherInstitution() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构L1");
        Long otherInstitutionId = institutionDao.insert("管理端学生测试机构L2");
        teacherDao.insert(new Teacher(null, institutionId, "13800012018",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, otherInstitutionId, "13800012019",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long otherClassRoomId = teachingUnitDao.insert(new TeachingUnit(null, otherInstitutionId, otherTeacherId,
                "别的机构的班", BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "学生", null, true, null, null));
        String token = login("13800012018", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\",\"teachingUnitId\":" + otherClassRoomId + ",\"enrolled\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminUpdateRejectsLessonCountTeachingUnitAsAssignment() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构M");
        teacherDao.insert(new Teacher(null, institutionId, "13800012020",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800012021",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "课外课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "学生", null, true, null, null));
        String token = login("13800012020", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\",\"teachingUnitId\":" + courseId + ",\"enrolled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminDeactivatesAndReactivatesStudentRecordingAuditLog() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构N");
        teacherDao.insert(new Teacher(null, institutionId, "13800012022",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "停用生", null, true, null, null));
        String token = login("13800012022", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"停用生\",\"enrolled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(false));

        Integer deactivateAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'STUDENT_DEACTIVATE' AND target_id = ?",
                Integer.class, studentId);
        assertThat(deactivateAuditCount).isEqualTo(1);

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"停用生\",\"enrolled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled").value(true));

        Integer enableAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'STUDENT_ENABLE' AND target_id = ?",
                Integer.class, studentId);
        assertThat(enableAuditCount).isEqualTo(1);
    }

    @Test
    void adminUpdateForStudentInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("管理端学生测试机构O1");
        Long institutionBId = institutionDao.insert("管理端学生测试机构O2");
        teacherDao.insert(new Teacher(null, institutionAId, "13800012023",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long studentBId = studentDao.insert(new Student(null, institutionBId, null, "别的机构的学生", null, true, null, null));
        String token = login("13800012023", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentBId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\",\"enrolled\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromUpdatingStudent() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构P");
        teacherDao.insert(new Teacher(null, institutionId, "13800012024",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "学生", null, true, null, null));
        String token = login("13800012024", "password");

        mockMvc.perform(patch("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生\",\"enrolled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listOrdersCustodyStudentsByTeacherThenPureOffCampusStudentsByFirstCourseName() throws Exception {
        Long institutionId = institutionDao.insert("管理端学生测试机构Q");
        teacherDao.insert(new Teacher(null, institutionId, "13800012025",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800012026", "A老师",
                passwordEncoder.encode("teacher-password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800012027", "B老师",
                passwordEncoder.encode("teacher-password-b"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long classRoomBId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherBId, "B班",
                BillingMode.MONTHLY, null, null, true, null));
        Long mathCourseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "数学课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long chineseCourseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "语文课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        String adminToken = login("13800012025", "admin-password");

        // 托管学生：分别挂 B 老师和 A 老师的班，预期排序结果 A 老师的在前。
        studentDao.insert(new Student(null, institutionId, classRoomBId, "托管乙", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomAId, "托管甲", "一班", true, null, null));
        // 纯课外课学生：分别只报了数学课、语文课，预期按课程名排序"数学课"在"语文课"前面。
        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"课外乙\",\"courseIds\":[" + chineseCourseId + "]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/students")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"课外甲\",\"courseIds\":[" + mathCourseId + "]}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        // MockHttpServletResponse#getContentAsString() 不传参数时默认按 ISO-8859-1 解码，
        // 中文会被拆成乱码——必须显式指定 UTF-8，这也是账单/学生这些接口响应体本身的
        // 真实编码。jsonPath() 断言内部已经处理好了，这里是手动解析 JSON 才会踩到。
        List<String> order = new java.util.ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .forEach(node -> order.add(node.get("name").asText()));

        // 托管组（A老师、B老师）排在纯课外组前面；托管组内按教师名排序 A老师 < B老师；
        // 纯课外组内按首门课程名排序"数学课" < "语文课"。
        assertThat(order.indexOf("托管甲")).isLessThan(order.indexOf("托管乙"));
        assertThat(order.indexOf("托管乙")).isLessThan(order.indexOf("课外甲"));
        assertThat(order.indexOf("课外甲")).isLessThan(order.indexOf("课外乙"));
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
