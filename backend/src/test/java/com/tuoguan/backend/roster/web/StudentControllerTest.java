package com.tuoguan.backend.roster.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentControllerTest extends IntegrationTestBase {

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
    void listsAndUpdatesStudentInOwnClass() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构A");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800008001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long classRoomId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherId, "托管班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "三年级2班",
                true, null, null));
        String token = login("13800008001", "password");

        mockMvc.perform(get("/api/classes/" + classRoomId + "/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("小明"));

        mockMvc.perform(put("/api/students/" + studentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小明明\",\"schoolClassName\":\"三年级3班\",\"enrolled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("小明明"))
                .andExpect(jsonPath("$.schoolClassName").value("三年级3班"))
                .andExpect(jsonPath("$.enrolled").value(false));
    }

    @Test
    void teacherCannotAccessAnotherTeachersClassOrStudents() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构B");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800008002",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800008003",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));

        String tokenB = login("13800008003", "password-b");

        mockMvc.perform(get("/api/classes/" + classRoomAId + "/students")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCannotUpdateAnotherTeachersStudent() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构C");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800008004",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800008005",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小刚", "五年级1班",
                true, null, null));

        String tokenB = login("13800008005", "password-b");

        mockMvc.perform(put("/api/students/" + studentId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改名\",\"schoolClassName\":\"五年级2班\",\"enrolled\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCanFetchShareLinkForOwnStudentButNotAnothers() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构D");
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800008006",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800008007",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        Long classRoomAId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, teacherAId, "A班",
                BillingMode.MONTHLY, null, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, classRoomAId, "小周", "一年级1班",
                true, null, null));

        String tokenA = login("13800008006", "password-a");
        String tokenB = login("13800008007", "password-b");

        mockMvc.perform(get("/api/students/" + studentId + "/share-link")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(get("/api/students/" + studentId + "/share-link")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void courseTeacherCanManagePureOffCampusStudentButUnrelatedTeacherCannot() throws Exception {
        Long institutionId = institutionDao.insert("学生控制器测试机构E");
        Long courseTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13800008008",
                passwordEncoder.encode("course-teacher-password"), Role.TEACHER, false, null));
        Long unrelatedTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13800008009",
                passwordEncoder.encode("unrelated-teacher-password"), Role.TEACHER, false, null));
        Long courseId = teachingUnitDao.insert(new TeachingUnit(null, institutionId, courseTeacherId, "围棋课",
                BillingMode.LESSON_COUNT, 60, null, true, null));
        Long studentId = studentDao.insert(new Student(null, institutionId, null, "小外", null, true, null, null));
        enrollmentDao.insert(new StudentUnitEnrollment(null, institutionId, studentId, courseId, true, null));

        String courseTeacherToken = login("13800008008", "course-teacher-password");
        String unrelatedTeacherToken = login("13800008009", "unrelated-teacher-password");

        mockMvc.perform(get("/api/students/" + studentId + "/share-link")
                        .header("Authorization", "Bearer " + courseTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(put("/api/students/" + studentId)
                        .header("Authorization", "Bearer " + courseTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"小外改名\",\"schoolClassName\":null,\"enrolled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("小外改名"));

        mockMvc.perform(get("/api/students/" + studentId + "/share-link")
                        .header("Authorization", "Bearer " + unrelatedTeacherToken))
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
