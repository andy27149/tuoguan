package com.tuoguan.backend.auth.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

// 产品诊断 #57：登录接口此前只校验密码匹配，没有任何失败次数限制，/api/auth/login
// 可以被无限速率撞库。单机单实例部署（技术架构文档已记录），不引入 Redis，用内存
// 记录就够——重启后计数清零是可接受的代价，跟现有 JWT 不可撤销的权衡是同一个量级。
// 只做"应用层账号锁定"这部分；nginx 层限速是基础设施层面的另一半防护，不在这次
// 代码改动范围内。
@Component
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final Clock clock;
    private final ConcurrentHashMap<String, Attempt> attemptsByPhone = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // 包内可见，供单元测试注入可控的 Clock，不用真的等 15 分钟验证窗口/锁定过期逻辑。
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    // 判断是否已锁定、以及把本次尝试计入次数，必须是同一次原子操作：如果分成"先查是否
    // 锁定"和"校验完密码再记一次失败"两步（调用方中间隔着一次数据库查询 + bcrypt 比对，
    // 耗时几十毫秒），并发请求会全部读到"未锁定"，实际能绕过的尝试次数就不再是 5 次，
    // 而是约等于服务器当时能并行处理的请求数——这是 review 发现的真实漏洞，不是假设。
    // 调用方应当在做密码校验之前先调用这个方法；返回 true 时直接拒绝、不要再做密码校验。
    public boolean isLockedAndRegisterAttempt(String phone) {
        Instant now = clock.instant();
        boolean[] wasLocked = new boolean[1];
        attemptsByPhone.compute(phone, (key, existing) -> {
            if (existing != null && existing.lockedUntil() != null) {
                if (existing.lockedUntil().isAfter(now)) {
                    wasLocked[0] = true;
                    return existing;
                }
                // 锁定期已过，等同于重新开始计数。
                existing = null;
            }
            boolean windowExpired = existing == null || existing.windowStart().plus(WINDOW).isBefore(now);
            Instant windowStart = windowExpired ? now : existing.windowStart();
            int count = windowExpired ? 1 : existing.count() + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? now.plus(LOCKOUT) : null;
            return new Attempt(windowStart, count, lockedUntil);
        });
        return wasLocked[0];
    }

    public void recordSuccess(String phone) {
        attemptsByPhone.remove(phone);
    }

    private record Attempt(Instant windowStart, int count, Instant lockedUntil) {
    }
}
