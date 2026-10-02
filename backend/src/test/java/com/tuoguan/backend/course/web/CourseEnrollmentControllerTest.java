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
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsPureOffCampusStudentAndAutoEnrollsInOwnCourse() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "数学课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13900031001", "password");

        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小外\",\"schoolClassName\":\"七年级1班\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("小外"))
                .andExpect(jsonPath("$.offCampusOnly").value(true));

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("小外"));
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
                .andExpect(jsonPath("$[0].offCampusOnly").value(false));
    }

    @Test
    void unenrollingRemovesStudentFromRoster() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构C");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031003",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "语文课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13900031003", "password");

        MvcResult createResult = mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小外\",\"schoolClassName\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        CourseRosterEntry created = objectMapper.readValue(createResult.getResponse().getContentAsString(),
                CourseRosterEntry.class);

        mockMvc.perform(delete("/api/courses/" + courseId + "/enrollments/" + created.studentId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void operatingOnAnotherTeachersCourseReturnsNotFound() throws Exception {
        Long institutionId = institutionDao.insert("报名控制器测试机构D");
        Long ownerTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13900031004",
                passwordEncoder.encode("owner-password"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13900031005",
                passwordEncoder.encode("intruder-password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, ownerTeacherId, "科学课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String intruderToken = login("13900031005", "intruder-password");

        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小外\",\"schoolClassName\":null}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/courses/" + courseId + "/roster")
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
