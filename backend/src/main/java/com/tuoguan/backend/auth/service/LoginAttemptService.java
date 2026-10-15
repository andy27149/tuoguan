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

    public void recordFailure(String phone) {
        Instant now = clock.instant();
        attemptsByPhone.compute(phone, (key, existing) -> {
            if (existing == null || existing.windowStart().plus(WINDOW).isBefore(now)) {
                return new Attempt(now, 1, null);
            }
            int count = existing.count() + 1;
            Instant lockedUntil = count >= MAX_ATTEMPTS ? now.plus(LOCKOUT) : existing.lockedUntil();
            return new Attempt(existing.windowStart(), count, lockedUntil);
        });
    }

    public void recordSuccess(String phone) {
        attemptsByPhone.remove(phone);
    }

    public boolean isLocked(String phone) {
        Attempt attempt = attemptsByPhone.get(phone);
        if (attempt == null || attempt.lockedUntil() == null) {
            return false;
        }
        if (attempt.lockedUntil().isBefore(clock.instant())) {
            // 锁定期已过，顺手清掉这条记录，等于重新开始计数。
            attemptsByPhone.remove(phone);
            return false;
        }
        return true;
    }

    private record Attempt(Instant windowStart, int count, Instant lockedUntil) {
    }
}
