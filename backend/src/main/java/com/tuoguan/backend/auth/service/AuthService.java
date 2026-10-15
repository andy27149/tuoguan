package com.tuoguan.backend.auth.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.security.JwtService;
import com.tuoguan.backend.auth.web.AccountLockedException;
import com.tuoguan.backend.auth.web.LoginResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final TeacherDao teacherDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(TeacherDao teacherDao, PasswordEncoder passwordEncoder, JwtService jwtService,
                        LoginAttemptService loginAttemptService) {
        this.teacherDao = teacherDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    public LoginResponse login(String phone, String rawPassword) {
        // phone 为 null/空（缺字段或显式传 null）本来就查不到任何老师，直接当凭证错误处理；
        // 不加这个前置判断的话会把 null 传进 ConcurrentHashMap.compute()，NPE 变成 500，
        // 这是 review 发现的一个回归，必须在走到限流检查之前就挡掉。
        if (phone == null || phone.isBlank()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (loginAttemptService.isLockedAndRegisterAttempt(phone)) {
            throw new AccountLockedException("Too many failed login attempts");
        }
        Teacher teacher = teacherDao.findByPhone(phone)
                .filter(t -> passwordEncoder.matches(rawPassword, t.passwordHash()))
                .orElse(null);
        if (teacher == null) {
            throw new BadCredentialsException("Invalid credentials");
        }
        loginAttemptService.recordSuccess(phone);
        String token = jwtService.issueToken(teacher.id(), teacher.institutionId(), teacher.role());
        return new LoginResponse(token, teacher.mustChangePassword());
    }

    public void changePassword(Long teacherId, String oldPassword, String newPassword) {
        Teacher teacher = teacherDao.findById(teacherId)
                .orElseThrow(() -> new IllegalStateException("Authenticated teacher not found: " + teacherId));
        if (!passwordEncoder.matches(oldPassword, teacher.passwordHash())) {
            throw new BadCredentialsException("Old password does not match");
        }
        teacherDao.updatePassword(teacherId, passwordEncoder.encode(newPassword));
    }
}
