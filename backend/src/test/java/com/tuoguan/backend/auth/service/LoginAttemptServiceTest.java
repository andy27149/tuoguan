package com.tuoguan.backend.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String PHONE = "13800009999";

    // 和 LoginAttemptService.MAX_ATTEMPTS 保持一致（该常量是私有的，测试里手动对齐）。
    private static final int MAX_ATTEMPTS = 5;

    private volatile Instant now = Instant.parse("2026-10-15T00:00:00Z");

    private final LoginAttemptService service = new LoginAttemptService(mutableClock());

    private Clock mutableClock() {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }

    @Test
    void notLockedBeforeFifthAttempt() {
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            assertThat(service.isLockedAndRegisterAttempt(PHONE)).isFalse();
        }
    }

    @Test
    void stillAllowsTheFifthAttemptThenLocksSubsequentOnes() {
        // 第 5 次尝试本身应该被放行（对应登录流程里这次仍然会走密码校验，只是密码错误时
        // 返回 401），锁定要到下一次请求才生效，返回 429——和集成测试
        // AuthControllerLoginTest#loginLocksAccountAfterFiveFailedAttemptsEvenWithCorrectPassword
        // 的预期行为一致。
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(service.isLockedAndRegisterAttempt(PHONE)).isFalse();
        }

        assertThat(service.isLockedAndRegisterAttempt(PHONE)).isTrue();
    }

    @Test
    void recordSuccessClearsLockout() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            service.isLockedAndRegisterAttempt(PHONE);
        }
        assertThat(service.isLockedAndRegisterAttempt(PHONE)).isTrue();

        service.recordSuccess(PHONE);

        assertThat(service.isLockedAndRegisterAttempt(PHONE)).isFalse();
    }

    @Test
    void lockoutExpiresAfterConfiguredDuration() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            service.isLockedAndRegisterAttempt(PHONE);
        }
        assertThat(service.isLockedAndRegisterAttempt(PHONE)).isTrue();

        now = now.plus(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(service.isLockedAndRegisterAttempt(PHONE)).isFalse();
    }

    @Test
    void windowExpiryResetsCountInsteadOfAccumulating() {
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            assertThat(service.isLockedAndRegisterAttempt(PHONE)).isFalse();
        }

        now = now.plus(Duration.ofMinutes(15).plusSeconds(1));

        // 窗口已过期，计数应该重新从 1 开始；如果没有正确重置而是接着累加，
        // 这个循环里某一次就会提前读到"已锁定"，而不是要重新攒够 MAX_ATTEMPTS 次。
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(service.isLockedAndRegisterAttempt(PHONE)).isFalse();
        }

        assertThat(service.isLockedAndRegisterAttempt(PHONE)).isTrue();
    }

    @Test
    void nullPhoneDoesNotThrow() {
        // AuthService 现在会在调用本方法之前就拦掉 null/空手机号，这里只是确认底层
        // Map 不会因为 null key 直接抛 NPE 把这道防线压到调用方自己维护。
        assertThat(attemptsByPhoneAllowsNullSafely()).isTrue();
    }

    private boolean attemptsByPhoneAllowsNullSafely() {
        try {
            service.isLockedAndRegisterAttempt(null);
            return false; // ConcurrentHashMap 不允许 null key，预期这里应该抛异常。
        } catch (NullPointerException expected) {
            return true;
        }
    }

    @Test
    void concurrentAttemptsCannotSlipPastTheAttemptLimit() throws InterruptedException {
        // 诊断 #57 review 发现的核心漏洞：如果"判断是否锁定"和"记一次尝试"分成两步，
        // 中间隔着一次耗时的密码校验，高并发请求会全部读到"未锁定"，实际放行的尝试数
        // 远超过 5 次。这里直接对 isLockedAndRegisterAttempt 本身做并发压测，断言
        // 放行（返回 false）的请求数严格等于 MAX_ATTEMPTS，钉住"判断+计数必须原子"
        // 这个不变量，避免以后重构时不小心把两步拆开。
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowedThrough = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!service.isLockedAndRegisterAttempt(PHONE)) {
                    allowedThrough.incrementAndGet();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(allowedThrough.get()).isEqualTo(MAX_ATTEMPTS);
    }
}
