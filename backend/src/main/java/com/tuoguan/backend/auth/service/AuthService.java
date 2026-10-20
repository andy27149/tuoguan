package com.tuoguan.backend.auth.service;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.domain.Teacher;
import com.tuoguan.backend.auth.security.JwtService;
import com.tuoguan.backend.auth.web.AccountLockedException;
import com.tuoguan.backend.auth.web.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

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
            log.warn("登录被锁定拦截：phone={}", maskPhone(phone));
            throw new AccountLockedException("Too many failed login attempts");
        }
        Teacher teacher = teacherDao.findByPhone(phone)
                .filter(t -> passwordEncoder.matches(rawPassword, t.passwordHash()))
                .orElse(null);
        if (teacher == null) {
            log.warn("登录失败（手机号不存在或密码不匹配）：phone={}", maskPhone(phone));
            throw new BadCredentialsException("Invalid credentials");
        }
        loginAttemptService.recordSuccess(phone);
        log.info("登录成功：teacherId={}, institutionId={}", teacher.id(), teacher.institutionId());
        String token = jwtService.issueToken(teacher.id(), teacher.institutionId(), teacher.role());
        return new LoginResponse(token, teacher.mustChangePassword());
    }

    // 日志只保留手机号首尾各几位，避免完整手机号被明文写进容器日志——日志本身不出仓库、
    // 不发给任何第三方，但容器日志的访问权限和数据库的访问权限不是同一套控制，没必要
    // 让可以看日志的人也等于拿到了完整手机号。
    private static String maskPhone(String phone) {
        if (phone.length() <= 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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
