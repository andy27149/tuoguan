package com.tuoguan.backend.auth.security;

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

// code review on #66/#61：MustChangePasswordFilter 跑在 Spring Security 的 filter
// chain 里，在 DispatcherServlet 之前执行——这里面的数据库查询抛异常，不会被
// RosterExceptionHandler 的全局兜底捕获到（那个兜底只覆盖 DispatcherServlet 分发
// 之后的异常），此前会直接裸奔到 Spring Boot 默认的 /error 页面，响应体格式和其它
// 接口不一致，也没有走统一的日志记录。这里验证修复后的行为：和 RosterExceptionHandler
// 一样的日志约定 + 响应体约定。
class MustChangePasswordFilterTest extends IntegrationTestBase {

    @Autowired
    private InstitutionDao institutionDao;

    @SpyBean
    private TeacherDao teacherDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unexpectedExceptionDuringMustChangePasswordCheckReturnsGenericServerError() throws Exception {
        Long institutionId = institutionDao.insert("改密检查异常兜底测试机构");
        String adminPhone = uniquePhone();
        teacherDao.insert(new Teacher(null, institutionId, adminPhone,
                passwordEncoder.encode("admin-password"), Role.ADMIN, false, null));
        String adminToken = login(adminPhone, "admin-password");

        // 登录时已经查过一次 teacherDao，这里只在后续请求里才让它抛异常，避免连登录本身
        // 都失败导致拿不到 token。
        doThrow(new RuntimeException("模拟一个没人预料到的异常")).when(teacherDao).findById(anyLong());

        mockMvc.perform(get("/api/teachers/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("服务器内部错误，请稍后重试"));
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
