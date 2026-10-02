package com.tuoguan.backend.course.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.tuoguan.backend.unit.dao.TeachingUnitDao;
import com.tuoguan.backend.unit.domain.BillingMode;
import com.tuoguan.backend.unit.domain.TeachingUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCourseControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private TeachingUnitDao courseDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsAllCoursesIncludingUnpricedAndInactive() throws Exception {
        Long institutionId = institutionDao.insert("管理端课程测试机构A");
        Long adminId = teacherDao.insert(new Teacher(null, institutionId, "13800011001",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800011002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "未定价课", BillingMode.LESSON_COUNT, 45, null, true, null));
        Long inactiveCourseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "已停用课",
                BillingMode.LESSON_COUNT, 45, new java.math.BigDecimal("30.00"), true, null));
        courseDao.setActive(inactiveCourseId, false);
        String token = login("13800011001", "password");

        mockMvc.perform(get("/api/admin/courses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void patchUpdatesPriceTeacherAndActiveIndependentlyOrTogether() throws Exception {
        Long institutionId = institutionDao.insert("管理端课程测试机构B");
        Long adminId = teacherDao.insert(new Teacher(null, institutionId, "13800011003",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherAId = teacherDao.insert(new Teacher(null, institutionId, "13800011004",
                passwordEncoder.encode("teacher-a"), Role.TEACHER, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionId, "13800011005",
                passwordEncoder.encode("teacher-b"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherAId, "数学课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13800011003", "password");

        mockMvc.perform(patch("/api/admin/courses/" + courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pricePerLesson\":50.00}"))
                .andExpect(status().isOk());
        assertPriceIs(courseId, "50.00");

        mockMvc.perform(patch("/api/admin/courses/" + courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherId\":" + teacherBId + ",\"active\":false}"))
                .andExpect(status().isOk());

        TeachingUnit updated = courseDao.findById(courseId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.teacherId()).isEqualTo(teacherBId);
        org.assertj.core.api.Assertions.assertThat(updated.active()).isFalse();
        org.assertj.core.api.Assertions.assertThat(updated.pricePerLesson()).isEqualByComparingTo("50.00");
    }

    private void assertPriceIs(Long courseId, String expected) {
        TeachingUnit course = courseDao.findById(courseId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(course.pricePerLesson()).isEqualByComparingTo(expected);
    }

    @Test
    void crossInstitutionCourseAccessReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("管理端课程测试机构C1");
        Long institutionBId = institutionDao.insert("管理端课程测试机构C2");
        teacherDao.insert(new Teacher(null, institutionAId, "13800011006",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13800011007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long courseInB = courseDao.insert(new TeachingUnit(null, institutionBId, teacherBId, "别人机构的课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String tokenA = login("13800011006", "password");

        mockMvc.perform(patch("/api/admin/courses/" + courseInB)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherRoleCannotCallAdminCourseEndpoints() throws Exception {
        Long institutionId = institutionDao.insert("管理端课程测试机构D");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800011008",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        Long courseId = courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "美术课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13800011008", "password");

        mockMvc.perform(get("/api/admin/courses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/courses/" + courseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesCourseAndAssignsTeacher() throws Exception {
        Long institutionId = institutionDao.insert("管理端课程测试机构E");
        teacherDao.insert(new Teacher(null, institutionId, "13800011009",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800011010",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13800011009", "password");

        mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新课\",\"lessonDurationMinutes\":45,\"teacherId\":" + teacherId
                                + ",\"pricePerLesson\":60.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("新课"))
                .andExpect(jsonPath("$.teacherId").value(teacherId))
                .andExpect(jsonPath("$.pricePerLesson").value(60.00))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminCreateCourseRejectsDuplicateNameWithinInstitution() throws Exception {
        Long institutionId = institutionDao.insert("管理端课程测试机构F");
        teacherDao.insert(new Teacher(null, institutionId, "13800011011",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800011012",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        courseDao.insert(new TeachingUnit(null, institutionId, teacherId, "重名课", BillingMode.LESSON_COUNT, 45, null, true, null));
        String token = login("13800011011", "password");

        mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重名课\",\"lessonDurationMinutes\":45,\"teacherId\":" + teacherId + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCreateCourseRejectsTeacherFromAnotherInstitution() throws Exception {
        Long institutionAId = institutionDao.insert("管理端课程测试机构G1");
        Long institutionBId = institutionDao.insert("管理端课程测试机构G2");
        teacherDao.insert(new Teacher(null, institutionAId, "13800011013",
                passwordEncoder.encode("password"), Role.ADMIN, false, null));
        Long teacherInB = teacherDao.insert(new Teacher(null, institutionBId, "13800011014",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13800011013", "password");

        mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新课\",\"lessonDurationMinutes\":45,\"teacherId\":" + teacherInB + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherRoleCannotCallAdminCreateCourseEndpoint() throws Exception {
        Long institutionId = institutionDao.insert("管理端课程测试机构H");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13800011015",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13800011015", "password");

        mockMvc.perform(post("/api/admin/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新课\",\"lessonDurationMinutes\":45,\"teacherId\":" + teacherId + "}"))
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
