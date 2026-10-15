package com.tuoguan.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuoguan.backend.auth.dao.InstitutionDao;
import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Role;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.web.LoginResponse;
import com.tuoguan.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 产品诊断 #66：验证真正没人认识的异常会被兜底成统一的 500 + 友好文案，而不是裸奔到
// 前端；同时验证 @Valid 参数校验失败这类 Spring 早就正确处理的场景状态码不受影响
// （GlobalExceptionHandler 继承 ResponseEntityExceptionHandler 而不是裸写
// @ExceptionHandler(Exception.class) 正是为了不吞掉这类场景）。
class GlobalExceptionHandlerTest extends IntegrationTestBase {

    @Autowired
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private InstitutionDao institutionDao;

    @Test
    void unexpectedExceptionIsMappedToGenericServerError() throws Exception {
        Long institutionId = institutionDao.insert("全局异常兜底测试机构");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login(adminPhone, "admin-password");

        doThrow(new RuntimeException("模拟一个没人预料到的异常"))
                .when(institutionDao).findById(anyLong());

        mockMvc.perform(get("/api/admin/institution")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("服务器内部错误，请稍后重试"));
    }

    @Test
    void validationFailureStillReturnsBadRequestNotSwallowedByGenericHandler() throws Exception {
        // CreateTeacherRequest.phone 标了 @NotBlank；这条请求故意传空字符串，验证
        // GlobalExceptionHandler 没有把 MethodArgumentNotValidException 也吞成 500。
        Long institutionId = institutionDao.insert("校验不受影响测试机构");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login(adminPhone, "admin-password");

        mockMvc.perform(post("/api/admin/teachers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"\",\"name\":\"测试老师\",\"initialPassword\":\"Passw0rd!\",\"role\":\"TEACHER\"}"))
                .andExpect(status().isBadRequest());
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
