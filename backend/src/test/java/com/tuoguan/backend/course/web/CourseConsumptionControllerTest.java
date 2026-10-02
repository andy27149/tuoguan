package com.tuoguan.backend.course.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseConsumptionControllerTest extends IntegrationTestBase {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recordsConsumptionForBothClassRoomAndOffCampusStudentsSharingACourse() throws Exception {
        Long institutionId = institutionDao.insert("消课控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800010001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课", BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long classRoomId = classRoomDao.insert(new TeachingUnit(null, institutionId, teacherId, "一年级1班", BillingMode.MONTHLY, null, null, true, null));
        Long classRoomStudentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "小托", "一年级1班", true, null, null));
        Long offCampusStudentId = studentDao.insert(
                new Student(null, institutionId, null, "小外", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, classRoomStudentId, courseId, true, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, offCampusStudentId, courseId, true, null));
        String token = login("13800010001", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + classRoomStudentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceSnapshot").value(50.00));

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + offCampusStudentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceSnapshot").value(50.00));
    }

    @Test
    void sameDayDuplicateConsumptionRequiresConfirmation() throws Exception {
        Long institutionId = institutionDao.insert("消课控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800010002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "英语课", BillingMode.LESSON_COUNT, 45, new BigDecimal("60.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String token = login("13800010002", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void consumptionOnUnpricedCourseReturnsBadRequest() throws Exception {
        Long institutionId = institutionDao.insert("消课控制器测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800010003",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "美术课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String token = login("13800010003", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consumptionForNotEnrolledStudentReturnsBadRequest() throws Exception {
        Long institutionId = institutionDao.insert("消课控制器测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800010004",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "体育课", BillingMode.LESSON_COUNT, 45, new BigDecimal("40.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        String token = login("13800010004", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consumptionOnAnotherTeachersCourseReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("消课控制器测试机构E");
        Long ownerTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13800010005",
                passwordEncoder.encode("owner-password"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13800010006",
                passwordEncoder.encode("intruder-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, ownerTeacherId, "书法课", BillingMode.LESSON_COUNT, 45, new BigDecimal("30.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String intruderToken = login("13800010006", "intruder-password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentId + ",\"date\":\"2024-01-02\",\"confirm\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchConsumptionCreatesRecordsOnlyForPresentStudents() throws Exception {
        Long institutionId = institutionDao.insert("批量消课测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800020001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "书法课", BillingMode.LESSON_COUNT, 45, new BigDecimal("50.00"), true, null));
        Long presentStudentId = studentDao.insert(new Student(null, institutionId, null, "小明", null, true, null, null));
        Long absentStudentId = studentDao.insert(new Student(null, institutionId, null, "小红", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, presentStudentId, courseId, true, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, absentStudentId, courseId, true, null));
        String token = login("13800020001", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-02\",\"presentStudentIds\":[" + presentStudentId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].studentId").value(presentStudentId))
                .andExpect(jsonPath("$[0].priceSnapshot").value(50.00));
    }

    @Test
    void repeatedBatchConsumptionOnSameDaySilentlySkipsAlreadyRecordedStudents() throws Exception {
        Long institutionId = institutionDao.insert("批量消课测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800020002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "美术课", BillingMode.LESSON_COUNT, 45, new BigDecimal("40.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小明", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String token = login("13800020002", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-02\",\"presentStudentIds\":[" + studentId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-02\",\"presentStudentIds\":[" + studentId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void batchConsumptionOnUnpricedCourseReturnsBadRequest() throws Exception {
        Long institutionId = institutionDao.insert("批量消课测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800020003",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "围棋课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小明", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String token = login("13800020003", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-02\",\"presentStudentIds\":[" + studentId + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchConsumptionOnAnotherTeachersCourseReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("批量消课测试机构D");
        Long ownerTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13800020004",
                passwordEncoder.encode("owner-password"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13800020005",
                passwordEncoder.encode("intruder-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, ownerTeacherId, "声乐课", BillingMode.LESSON_COUNT, 45, new BigDecimal("30.00"), true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小明", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));
        String intruderToken = login("13800020005", "intruder-password");

        mockMvc.perform(post("/api/courses/" + courseId + "/consumption/batch")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2024-01-02\",\"presentStudentIds\":[" + studentId + "]}"))
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
