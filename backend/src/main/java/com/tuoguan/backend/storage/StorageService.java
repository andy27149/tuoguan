package com.tuoguan.backend.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    // 生产环境实测复现：MinIO 容器进程启动了，不代表它已经真正开始监听端口——
    // docker-compose.prod.yml 里 backend 访问 MinIO 走的是公网域名、经过 nginx
    // 反向代理，MinIO 还没就绪时 nginx 会直接返回 502。这里原本没有重试，一旦撞上
    // 这个窗口就直接抛异常，Spring 容器初始化失败、整个 JVM 启动崩溃退出——哪怕
    // MinIO 只是比 backend 晚几秒钟就绪。加上重试，给 MinIO 一点缓冲时间，而不是
    // 一次探测失败就让整个应用起不来。
    private static final int ENSURE_BUCKET_MAX_ATTEMPTS = 10;
    private static final long ENSURE_BUCKET_RETRY_DELAY_MILLIS = 3000;

    private final MinioClient minioClient;
    private final String bucket;

    public StorageService(MinioClient minioClient, String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= ENSURE_BUCKET_MAX_ATTEMPTS; attempt++) {
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                if (attempt > 1) {
                    log.info("MinIO 连接在第 {} 次尝试后恢复正常：bucket={}", attempt, bucket);
                }
                return;
            } catch (Exception e) {
                lastFailure = e;
                if (attempt < ENSURE_BUCKET_MAX_ATTEMPTS) {
                    log.warn("连接 MinIO 确认 bucket 失败（第 {}/{} 次尝试，{} 秒后重试）：bucket={}, 原因={}",
                            attempt, ENSURE_BUCKET_MAX_ATTEMPTS, ENSURE_BUCKET_RETRY_DELAY_MILLIS / 1000, bucket,
                            e.getMessage());
                    try {
                        Thread.sleep(ENSURE_BUCKET_RETRY_DELAY_MILLIS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "Interrupted while retrying MinIO bucket check: " + bucket, interrupted);
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Failed to ensure MinIO bucket exists after " + ENSURE_BUCKET_MAX_ATTEMPTS + " attempts: " + bucket,
                lastFailure);
    }

    public String uploadAvatar(Long institutionId, Long studentId, String contentType, byte[] content) {
        String objectKey = "avatars/%d/%d/%s".formatted(institutionId, studentId, UUID.randomUUID());
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload avatar to MinIO: " + objectKey, e);
        }
        return objectKey;
    }

    public String uploadLogo(Long institutionId, String contentType, byte[] content) {
        String objectKey = "logos/%d/%s".formatted(institutionId, UUID.randomUUID());
        try (ByteArrayInputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload logo to MinIO: " + objectKey, e);
        }
        return objectKey;
    }

    public String avatarUrl(String objectKey) {
        if (objectKey == null) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate presigned avatar URL: " + objectKey, e);
        }
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete avatar from MinIO: " + objectKey, e);
        }
    }
}
