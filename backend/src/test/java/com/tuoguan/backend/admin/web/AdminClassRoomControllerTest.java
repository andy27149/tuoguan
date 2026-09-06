package com.tuoguan.backend.admin.web;

import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminClassRoomControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminSeesOwnInstitutionClassesWithTeacherPhone() throws Exception {
        Long institutionId = institutionDao.insert("管理员看班测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13600001001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600001002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管一班", null));
        String adminToken = login("13600001001", "admin-password");

        mockMvc.perform(get("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("托管一班"))
                .andExpect(jsonPath("$[0].teacherId").value(teacherId))
                .andExpect(jsonPath("$[0].teacherPhone").value("13600001002"));
    }

    @Test
    void adminDoesNotSeeClassesFromOtherInstitutions() throws Exception {
        Long institutionAId = institutionDao.insert("管理员看班测试机构B");
        Long institutionBId = institutionDao.insert("管理员看班测试机构C");
        teacherDao.insert(new Teacher(null, institutionAId, "13600001003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13600001004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "托管二班", null));
        String adminToken = login("13600001003", "admin-password");

        mockMvc.perform(get("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void nonAdminTeacherIsForbiddenFromListingAdminClasses() throws Exception {
        Long institutionId = institutionDao.insert("管理员看班测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13600001005",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13600001005", "teacher-password");

        mockMvc.perform(get("/api/admin/classes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreateClasses() throws Exception {
        Long institutionId = institutionDao.insert("管理员看班测试机构E");
        teacherDao.insert(new Teacher(null, institutionId, "13600001006",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login("13600001006", "admin-password");

        mockMvc.perform(post("/api/admin/classes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"托管三班\",\"teacherId\":1}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void deletionImpactReturnsStudentCount() throws Exception {
        Long institutionId = institutionDao.insert("班级删除测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13600002001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600002002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管四班", null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小明", "一班", true, null, null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小红", "二班", true, null, null));
        String adminToken = login("13600002001", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/deletion-impact")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCount").value(2));
    }

    @Test
    void deletesClassRoomWithoutStudents() throws Exception {
        Long institutionId = institutionDao.insert("班级删除测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13600002003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600002004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管五班", null));
        String adminToken = login("13600002003", "admin-password");

        mockMvc.perform(delete("/api/admin/classes/" + classRoomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(classRoomDao.findById(classRoomId)).isEmpty();
    }

    @Test
    void deletesClassRoomWithStudentsAndCascadesStudents() throws Exception {
        Long institutionId = institutionDao.insert("班级删除测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13600002005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600002006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管六班", null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小刚", "三班", true, null, null));
        String adminToken = login("13600002005", "admin-password");

        mockMvc.perform(delete("/api/admin/classes/" + classRoomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertThat(classRoomDao.findById(classRoomId)).isEmpty();
        assertThat(studentDao.findAllByClassRoomId(classRoomId)).isEmpty();
    }

    @Test
    void deletionImpactForClassRoomInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("班级删除测试机构D");
        Long institutionBId = institutionDao.insert("班级删除测试机构E");
        teacherDao.insert(new Teacher(null, institutionAId, "13600002007",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13600002008",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "托管七班", null));
        String adminToken = login("13600002007", "admin-password");

        mockMvc.perform(get("/api/admin/classes/" + classRoomId + "/deletion-impact")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteClassRoomInAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("班级删除测试机构F");
        Long institutionBId = institutionDao.insert("班级删除测试机构G");
        teacherDao.insert(new Teacher(null, institutionAId, "13600002009",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherBId = teacherDao.insert(new Teacher(null, institutionBId, "13600002010",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionBId, teacherBId, "托管八班", null));
        String adminToken = login("13600002009", "admin-password");

        mockMvc.perform(delete("/api/admin/classes/" + classRoomId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        assertThat(classRoomDao.findById(classRoomId)).isPresent();
    }

    @Test
    void nonAdminTeacherIsForbiddenFromDeletingClass() throws Exception {
        Long institutionId = institutionDao.insert("班级删除测试机构H");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13600002011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "托管九班", null));
        String token = login("13600002011", "teacher-password");

        mockMvc.perform(delete("/api/admin/classes/" + classRoomId)
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
