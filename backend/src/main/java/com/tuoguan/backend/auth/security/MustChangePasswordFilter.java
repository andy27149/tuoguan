package com.tuoguan.backend.auth.security;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MustChangePasswordFilter extends OncePerRequestFilter {

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
            Teacher teacher = teacherDao.findById(principal.teacherId()).orElse(null);
            if (teacher != null && teacher.mustChangePassword()) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"MUST_CHANGE_PASSWORD\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
