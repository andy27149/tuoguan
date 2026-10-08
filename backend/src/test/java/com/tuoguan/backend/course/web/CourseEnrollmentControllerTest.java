package com.tuoguan.backend.course.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.course.dao.CourseRechargeRecordDao;
import com.tuoguan.backend.course.domain.CourseRechargeRecord;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseEnrollmentControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao courseDao;

    @Autowired
    private TeachingUnitDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private StudentUnitEnrollmentDao enrollmentDao;

    @Autowired
    private CourseRechargeRecordDao rechargeRecordDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void enrollsPureOffCampusStudentIntoOwnCourse() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", "七年级1班", true, null, null));
        String token = login("13900031001", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("小外"))
                .andExpect(jsonPath("$[0].offCampusOnly").value(true))
                .andExpect(jsonPath("$[0].balance").value(0));
    }

    @Test
    void enrollsExistingClassRoomStudentIntoOwnCourse() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long classRoomId = classRoomDao.insert(new TeachingUnit(null, institutionId, teacherId, "一年级1班", BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小托", "一年级1班", true, null, null));
        String token = login("13900031002", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("小托"))
                .andExpect(jsonPath("$[0].offCampusOnly").value(false))
                .andExpect(jsonPath("$[0].balance").value(nullValue()));
    }

    @Test
    void showsBalanceForAClassRoomStudentOnceTheyHaveRechargedForThisCourse() throws Exception {
        // 托管班学生（双重身份）现在也允许预充值，充值过之后花名册也应该展示余额，
        // 不再像之前那样恒为 null。
        Long institutionId = institutionDao.insert("报名控制器测试机构B3");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031011",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "科学课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long classRoomId = classRoomDao.insert(new TeachingUnit(null, institutionId, teacherId, "二年级1班", BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小托2", "二年级1班", true, null, null));
        String token = login("13900031011", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());
        rechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId, 4, null, teacherId, null));

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offCampusOnly").value(false))
                .andExpect(jsonPath("$[0].balance").value(4));
    }

    @Test
    void enrollingAnAlreadyEnrolledStudentReturnsConflict() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构B2");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031030",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小托", null, true, null, null));
        String token = login("13900031030", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reEnrollingAfterUnenrollSucceeds() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构B3");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031031",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小托", null, true, null, null));
        String token = login("13900031031", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/courses/" + courseId + "/enrollments/" + studentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unenrollingRemovesStudentFromRoster() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031003",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "语文课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        String token = login("13900031003", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/courses/" + courseId + "/enrollments/" + studentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'COURSE_UNENROLL' AND target_id = ?",
                Integer.class, studentId);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void operatingOnAnotherTeachersCourseReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构D");
        Long ownerTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031004",
                passwordEncoder.encode("owner-password"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13900031005",
                passwordEncoder.encode("intruder-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, ownerTeacherId, "科学课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        String intruderToken = login("13900031005", "intruder-password");

        mockMvc.perform(post("/api/courses/" + courseId + "/enrollments")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + "}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rosterShowsNegativeBalanceForOffCampusStudentAfterOverConsumption() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构E");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031006",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "围棋课",
                BillingMode.LESSON_COUNT, 45, new java.math.BigDecimal("50.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        rechargeRecordDao.insert(new CourseRechargeRecord(null, institutionId, studentId, courseId, 1, null,
                teacherId, null));
        String token = login("13900031006", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-03\",\"confirm\":false}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(-1));
    }

    @Test
    void offCampusCandidatesListsUnenrolledInstitutionWideOffCampusStudentsOnly() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构F");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031007",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031008",
                passwordEncoder.encode("other-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "跆拳道课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        Long classRoomId = classRoomDao.insert(new TeachingUnit(null, institutionId, otherTeacherId, "二年级1班",
                BillingMode.MONTHLY, null, null, true, null));
        // 候选人：纯课外课、未报名本课程
        Long candidateId = studentDao.insert(new Student(null, institutionId, null, "小候选", "三年级1班",
                true, null, null));
        // 非候选人：已经报名了本课程的纯课外课学生
        Long alreadyEnrolledId = studentDao.insert(new Student(null, institutionId, null, "小已报", null,
                true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, alreadyEnrolledId, courseId, true, null));
        // 非候选人：有托管班的学生，即使没报名任何课程也不该出现在纯课外课候选人里
        studentDao.insert(new Student(null, institutionId, classRoomId, "小托管", "二年级1班", true, null, null));
        String token = login("13900031007", "password");

        mockMvc.perform(get("/api/courses/" + courseId + "/off-campus-candidates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(candidateId))
                .andExpect(jsonPath("$[0].name").value("小候选"))
                .andExpect(jsonPath("$[0].schoolClassName").value("三年级1班"));
    }

    @Test
    void offCampusCandidatesForAnotherTeachersCourseReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构G");
        Long ownerTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031009",
                passwordEncoder.encode("owner-password"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13900031010",
                passwordEncoder.encode("intruder-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, ownerTeacherId, "篮球课",
                BillingMode.LESSON_COUNT, 45, null, true, null));
        String intruderToken = login("13900031010", "intruder-password");

        mockMvc.perform(get("/api/courses/" + courseId + "/off-campus-candidates")
                        .header("Authorization", "Bearer " + intruderToken))
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
