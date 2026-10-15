package com.tuoguan.backend.auth.security;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MustChangePasswordFilter.class);

    private static final String CHANGE_PASSWORD_PATH = "/api/auth/change-password";

    private final TeacherDao teacherDao;

    public MustChangePasswordFilter(TeacherDao teacherDao) {
        this.teacherDao = teacherDao;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof TeacherPrincipal principal
                && !CHANGE_PASSWORD_PATH.equals(request.getRequestURI())) {
            Teacher teacher;
            // 这个过滤器跑在 Spring Security 的 filter chain 里，在 DispatcherServlet
            // 之前执行——这里的数据库查询一旦抛异常，不会被 RosterExceptionHandler 的
            // 全局兜底捕获到（那个兜底只覆盖 DispatcherServlet 分发之后的异常），会直接
            // 裸奔到 Spring Boot 默认的 /error 页面，响应体格式和其它接口的错误响应不
            // 一致，也不会走统一的日志记录（code review 发现）。这里单独兜一层，保持
            // 和 RosterExceptionHandler 一样的日志+响应体约定。
            try {
                teacher = teacherDao.findById(principal.teacherId()).orElse(null);
            } catch (RuntimeException e) {
                log.error("Unhandled exception while checking must-change-password for teacher {}",
                        principal.teacherId(), e);
                writeJsonError(response, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试");
                return;
            }
            if (teacher != null && teacher.mustChangePassword()) {
                writeJsonError(response, HttpStatus.FORBIDDEN, "MUST_CHANGE_PASSWORD");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    // setContentType("application/json") 不会顺带把字符编码设成 UTF-8——getWriter()
    // 默认用的是 ISO-8859-1，中文文案直接写进去会变成乱码（实测踩过：原来的
    // "MUST_CHANGE_PASSWORD" 全是 ASCII 字符没暴露这个问题，新加的中文错误文案暴露了）。
    // 必须在拿 writer 之前显式 setCharacterEncoding。
    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
