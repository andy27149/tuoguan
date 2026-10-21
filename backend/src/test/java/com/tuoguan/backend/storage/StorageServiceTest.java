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
