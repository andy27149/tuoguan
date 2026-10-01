package com.tuoguan.backend.course.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.course.dao.CourseDao;
import com.tuoguan.backend.course.domain.Course;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private CourseDao courseDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsCourseAndListsItForOwningTeacher() throws Exception {
        Long institutionId = institutionDao.insert("课程控制器测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13900030001",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        String token = login("13900030001", "password");

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"数学课\",\"lessonDurationMinutes\":60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("数学课"))
                .andExpect(jsonPath("$.lessonDurationMinutes").value(60))
                .andExpect(jsonPath("$.pricePerLesson").doesNotExist())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("数学课"));
    }

    @Test
    void rejectsDuplicateCourseNameForSameTeacher() throws Exception {
        Long institutionId = institutionDao.insert("课程控制器测试机构B");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13900030002",
                passwordEncoder.encode("password"), Role.TEACHER, false, null));
        courseDao.insert(new Course(null, institutionId, teacherId, "重名课", null, 45, true, null));
        String token = login("13900030002", "password");

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重名课\",\"lessonDurationMinutes\":45}"))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherOnlySeesOwnCoursesNotAnotherTeachersNewCourse() throws Exception {
        Long institutionId = institutionDao.insert("课程控制器测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13900030003",
                passwordEncoder.encode("password-a"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13900030004",
                passwordEncoder.encode("password-b"), Role.TEACHER, false, null));
        String tokenA = login("13900030003", "password-a");
        String tokenB = login("13900030004", "password-b");

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A老师的课\",\"lessonDurationMinutes\":45}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/courses")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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
