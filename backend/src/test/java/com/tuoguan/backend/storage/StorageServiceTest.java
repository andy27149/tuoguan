package com.tuoguan.backend.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 纯单元测试（不继承 IntegrationTestBase）：只测 ensureBucketExists 的重试/退避逻辑
// 本身，用 1ms 的重试间隔让测试保持快速。真实的 MinIO 交互已经由
// IntegrationTestBase 体系下的测试覆盖。
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    private static final String BUCKET = "avatars";

    @Mock
    private MinioClient minioClient;

    @Test
    void succeedsImmediatelyWithoutRetryWhenBucketAlreadyExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        new StorageService(minioClient, BUCKET, 5, 1);

        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void createsBucketWhenItDoesNotExistYet() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        new StorageService(minioClient, BUCKET, 5, 1);

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void retriesAfterTransientFailureThenSucceeds() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new IOException("connection refused"))
                .thenThrow(new IOException("connection refused"))
                .thenReturn(true);

        new StorageService(minioClient, BUCKET, 5, 1);

        verify(minioClient, times(3)).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    void rejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> new StorageService(minioClient, BUCKET, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-attempts");
    }

    @Test
    void rejectsNegativeRetryDelay() {
        assertThatThrownBy(() -> new StorageService(minioClient, BUCKET, 5, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delay-millis");
    }

    @Test
    void rejectsRetryBudgetExceedingSanityCap() {
        // 300_001ms，刚好超过 5 分钟的上限一毫秒，确认边界判断用的是 > 而不是 >=。
        assertThatThrownBy(() -> new StorageService(minioClient, BUCKET, 3, 100_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the sanity cap");
    }

    @Test
    void allowsRetryBudgetExactlyAtSanityCap() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // 3 * 100_000 = 300_000ms，正好等于上限，不应该被拒绝。
        new StorageService(minioClient, BUCKET, 3, 100_000);

        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    void preservesBothTheMinioFailureAndTheInterruptWhenRetrySleepIsInterrupted() throws Exception {
        IOException connectionRefused = new IOException("connection refused");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(connectionRefused);

        // 提前标记当前线程为"已中断"：Thread.sleep 发现这个标记会立刻抛
        // InterruptedException（并清掉标记），不需要真的并发打断另一个线程的
        // 时序竞态就能确定性地触发 sleepBeforeRetry 里的中断分支。
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new StorageService(minioClient, BUCKET, 5, 10_000))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Interrupted")
                    .hasCause(connectionRefused)
                    .satisfies(e -> assertThat(e.getSuppressed())
                            .hasAtLeastOneElementOfType(InterruptedException.class));
            assertThat(Thread.currentThread().isInterrupted())
                    .as("中断标记应该被恢复，不能被这段代码悄悄吞掉")
                    .isTrue();
        } finally {
            // 清掉中断标记，避免影响同一个线程里后面的测试。
            Thread.interrupted();
        }
    }

    @Test
    void throwsAfterExhaustingAllAttemptsWithLastFailureAsCauseAndEarlierOnesSuppressed() throws Exception {
        IOException first = new IOException("connection refused");
        IOException second = new IOException("connection refused");
        IOException last = new IOException("still unreachable");
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(first)
                .thenThrow(second)
                .thenThrow(last);

        assertThatThrownBy(() -> new StorageService(minioClient, BUCKET, 3, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 attempts")
                .hasCause(last)
                .satisfies(e -> assertThat(last.getSuppressed()).containsExactly(second))
                .satisfies(e -> assertThat(second.getSuppressed()).containsExactly(first));

        verify(minioClient, times(3)).bucketExists(any(BucketExistsArgs.class));
    }
}
