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
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
