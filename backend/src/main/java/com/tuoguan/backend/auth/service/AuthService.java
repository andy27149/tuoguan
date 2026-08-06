package com.tuoguan.backend.auth.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.security.JwtService;
import com.tuoguan.backend.auth.web.LoginResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(TeacherDao teacherDao, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String phone, String rawPassword) {
        Teacher teacher = teacherDao.findByPhone(phone)
                .filter(t -> passwordEncoder.matches(rawPassword, t.passwordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        String token = jwtService.issueToken(teacher.id(), teacher.institutionId(), teacher.role());
        return new LoginResponse(token, teacher.mustChangePassword());
    }
}
