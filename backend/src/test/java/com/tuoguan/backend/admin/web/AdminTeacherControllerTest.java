package com.tuoguan.backend.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.roster.dao.ClassRoomDao;
import com.tuoguan.backend.roster.dao.StudentDao;
import com.tuoguan.backend.roster.dao.TaskTemplateDao;
import com.tuoguan.backend.roster.domain.ClassRoom;
import com.tuoguan.backend.roster.domain.Student;
import com.tuoguan.backend.roster.domain.TaskTemplate;
import com.tuoguan.backend.support.IntegrationTestBase;
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

class AdminTeacherControllerTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassRoomDao classRoomDao;

    @Autowired
    private StudentDao studentDao;

    @Autowired
    private TaskTemplateDao taskTemplateDao;

    @Test
    void adminCreatesTeacherAccountWithoutExposingPassword() throws Exception {
        Long institutionId = institutionDao.insert("管理员教师测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13700001001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String token = login("13700001001", "admin-password");

        mockMvc.perform(post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700001002\",\"name\":\"李老师\",\"initialPassword\":\"initial123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("13700001002"))
                .andExpect(jsonPath("$.name").value("李老师"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.initialPassword").doesNotExist());
    }

    @Test
    void rejectsDuplicatePhoneWhenCreatingTeacher() throws Exception {
        Long institutionId = institutionDao.insert("管理员教师测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13700001003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        teacherDao.insert(new Teacher(null, institutionId, "13700001004",
                passwordEncoder.encode("existing"), Role.TEACHER, true, null));
        String token = login("13700001003", "admin-password");

        mockMvc.perform(post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700001004\",\"name\":\"王老师\",\"initialPassword\":\"initial123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromCreatingOrListingTeachers() throws Exception {
        Long institutionId = institutionDao.insert("管理员教师测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13700001005",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13700001005", "teacher-password");

        mockMvc.perform(post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13700001006\",\"name\":\"赵老师\",\"initialPassword\":\"initial123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOnlySeesTeachersFromOwnInstitution() throws Exception {
        Long institutionAId = institutionDao.insert("管理员教师测试机构D");
        Long institutionBId = institutionDao.insert("管理员教师测试机构E");
        teacherDao.insert(new Teacher(null, institutionAId, "13700001007",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        teacherDao.insert(new Teacher(null, institutionAId, "13700001008",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        teacherDao.insert(new Teacher(null, institutionBId, "13700001009",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13700001007", "admin-password");

        mockMvc.perform(get("/api/admin/teachers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deletesTeacherWithoutStudentsDirectly() throws Exception {
        Long institutionId = institutionDao.insert("删除教师测试机构A");
        teacherDao.insert(new Teacher(null, institutionId, "13700003001",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13700003002",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "空班级", null));
        Long templateId = taskTemplateDao.insert(
                new TaskTemplate(null, institutionId, teacherId, "语文", "预习", null, false));
        String token = login("13700003001", "admin-password");

        mockMvc.perform(get("/api/admin/teachers/" + teacherId + "/deletion-impact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classCount").value(1))
                .andExpect(jsonPath("$.studentCount").value(0))
                .andExpect(jsonPath("$.templateCount").value(1))
                .andExpect(jsonPath("$.hasStudents").value(false));

        mockMvc.perform(delete("/api/admin/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + token)
                        .param("mode", "DELETE_ALL"))
                .andExpect(status().isNoContent());

        assertThat(teacherDao.findById(teacherId)).isEmpty();
        assertThat(classRoomDao.findById(classRoomId)).isEmpty();
        assertThat(taskTemplateDao.findById(templateId)).isEmpty();
    }

    @Test
    void deletesTeacherWithStudentsUsingDeleteAllMode() throws Exception {
        Long institutionId = institutionDao.insert("删除教师测试机构B");
        teacherDao.insert(new Teacher(null, institutionId, "13700003003",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13700003004",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "有学生班级", null));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "小明", "一班", true, null, null));
        Long templateId = taskTemplateDao.insert(
                new TaskTemplate(null, institutionId, teacherId, "数学", "口算", null, false));
        String token = login("13700003003", "admin-password");

        mockMvc.perform(get("/api/admin/teachers/" + teacherId + "/deletion-impact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classCount").value(1))
                .andExpect(jsonPath("$.studentCount").value(1))
                .andExpect(jsonPath("$.templateCount").value(1))
                .andExpect(jsonPath("$.hasStudents").value(true));

        mockMvc.perform(delete("/api/admin/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + token)
                        .param("mode", "DELETE_ALL"))
                .andExpect(status().isNoContent());

        assertThat(teacherDao.findById(teacherId)).isEmpty();
        assertThat(classRoomDao.findById(classRoomId)).isEmpty();
        assertThat(studentDao.findById(studentId)).isEmpty();
        assertThat(taskTemplateDao.findById(templateId)).isEmpty();
    }

    @Test
    void deletesTeacherWithStudentsUsingTransferMode() throws Exception {
        Long institutionId = institutionDao.insert("删除教师测试机构C");
        teacherDao.insert(new Teacher(null, institutionId, "13700003005",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13700003006",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long targetTeacherId = teacherDao.insert(new Teacher(null, institutionId, "13700003007",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "转移班级", null));
        Long studentId = studentDao.insert(
                new Student(null, institutionId, classRoomId, "小红", "二班", true, null, null));
        Long templateId = taskTemplateDao.insert(
                new TaskTemplate(null, institutionId, teacherId, "英语", "单词", null, false));
        String token = login("13700003005", "admin-password");

        mockMvc.perform(delete("/api/admin/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + token)
                        .param("mode", "TRANSFER")
                        .param("targetTeacherId", String.valueOf(targetTeacherId)))
                .andExpect(status().isNoContent());

        assertThat(teacherDao.findById(teacherId)).isEmpty();
        assertThat(classRoomDao.findById(classRoomId)).get()
                .extracting(ClassRoom::teacherId).isEqualTo(targetTeacherId);
        assertThat(studentDao.findById(studentId)).isPresent();
        assertThat(taskTemplateDao.findById(templateId)).get()
                .extracting(TaskTemplate::teacherId).isEqualTo(targetTeacherId);
    }

    @Test
    void rejectsTransferToSelf() throws Exception {
        Long institutionId = institutionDao.insert("删除教师测试机构D");
        teacherDao.insert(new Teacher(null, institutionId, "13700003008",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13700003009",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        Long classRoomId = classRoomDao.insert(new ClassRoom(null, institutionId, teacherId, "自转移班级", null));
        studentDao.insert(new Student(null, institutionId, classRoomId, "小刚", "三班", true, null, null));
        String token = login("13700003008", "admin-password");

        mockMvc.perform(delete("/api/admin/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + token)
                        .param("mode", "TRANSFER")
                        .param("targetTeacherId", String.valueOf(teacherId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingTeacherFromAnotherInstitutionReturnsNotFound() throws Exception {
        Long institutionAId = institutionDao.insert("删除教师测试机构E");
        Long institutionBId = institutionDao.insert("删除教师测试机构F");
        teacherDao.insert(new Teacher(null, institutionAId, "13700003010",
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        Long otherTeacherId = teacherDao.insert(new Teacher(null, institutionBId, "13700003011",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13700003010", "admin-password");

        mockMvc.perform(get("/api/admin/teachers/" + otherTeacherId + "/deletion-impact")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/teachers/" + otherTeacherId)
                        .header("Authorization", "Bearer " + token)
                        .param("mode", "DELETE_ALL"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminTeacherIsForbiddenFromDeletingTeachers() throws Exception {
        Long institutionId = institutionDao.insert("删除教师测试机构G");
        Long teacherId = teacherDao.insert(new Teacher(null, institutionId, "13700003012",
                passwordEncoder.encode("teacher-password"), Role.TEACHER, false, null));
        String token = login("13700003012", "teacher-password");

        mockMvc.perform(delete("/api/admin/teachers/" + teacherId)
                        .header("Authorization", "Bearer " + token)
                        .param("mode", "DELETE_ALL"))
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
