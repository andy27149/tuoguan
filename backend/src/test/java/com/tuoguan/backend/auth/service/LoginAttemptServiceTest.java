package com.tuoguan.backend.auth.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String PHONE = "13800009999";

    private Instant now = Instant.parse("2026-10-15T00:00:00Z");

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
    void notLockedBeforeFifthFailure() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(PHONE);
        }

        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    void locksExactlyOnFifthFailureWithinWindow() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(PHONE);
        }

        assertThat(service.isLocked(PHONE)).isTrue();
    }

    @Test
    void recordSuccessClearsLockout() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(PHONE);
        }
        assertThat(service.isLocked(PHONE)).isTrue();

        service.recordSuccess(PHONE);

        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    void lockoutExpiresAfterConfiguredDuration() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure(PHONE);
        }
        assertThat(service.isLocked(PHONE)).isTrue();

        now = now.plus(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(service.isLocked(PHONE)).isFalse();
    }

    @Test
    void windowExpiryResetsCountInsteadOfAccumulating() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure(PHONE);
        }
        assertThat(service.isLocked(PHONE)).isFalse();

        now = now.plus(Duration.ofMinutes(15).plusSeconds(1));

        // 窗口已过期，这次失败应该重新从 1 计数，而不是变成第 5 次导致锁定。
        service.recordFailure(PHONE);

        assertThat(service.isLocked(PHONE)).isFalse();
    }
}
