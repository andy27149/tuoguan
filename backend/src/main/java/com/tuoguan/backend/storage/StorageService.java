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
    // 一次探测失败就让整个应用起不来。次数/间隔通过构造函数传入（来自
    // application.yml 的 minio.startup-retry.*，可用环境变量覆盖），不写死在
    // 代码里——生产环境如果发现这个窗口比预期更长，不需要改代码重新构建镜像。
    private final MinioClient minioClient;
    private final String bucket;
    private final int ensureBucketMaxAttempts;
    private final long ensureBucketRetryDelayMillis;

    public StorageService(MinioClient minioClient, String bucket, int ensureBucketMaxAttempts,
                           long ensureBucketRetryDelayMillis) {
        // 两个重试参数来自 application.yml，配置出错应该在这里就立刻、清楚地报出来，
        // 而不是留到 ensureBucketExists 内部才表现成一个莫名其妙的行为：次数 <= 0
        // 会让下面的循环体一次都不执行，MinIO 连通性检查形同虚设却悄悄"成功"退出
        // 构造函数（如果本来就该存在的 bucket 已经存在的话）；负数间隔会让
        // Thread.sleep 直接抛出不相关的 IllegalArgumentException，把设计好的、带
        // 完整失败原因的异常链路绕过去。
        if (ensureBucketMaxAttempts < 1) {
            throw new IllegalArgumentException(
                    "minio.startup-retry.max-attempts must be >= 1, got: " + ensureBucketMaxAttempts);
        }
        if (ensureBucketRetryDelayMillis < 0) {
            throw new IllegalArgumentException(
                    "minio.startup-retry.delay-millis must be >= 0, got: " + ensureBucketRetryDelayMillis);
        }
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.ensureBucketMaxAttempts = ensureBucketMaxAttempts;
        this.ensureBucketRetryDelayMillis = ensureBucketRetryDelayMillis;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        // 最终抛出的异常只把最后一次失败设为 cause——前面几次失败就算原因跟最后一次
        // 不一样（比如先是"连接被拒绝"，MinIO 起来了之后又因为别的原因失败），也不该
        // 在事后排查时彻底丢掉，挂成 suppressed exception 让它们在堆栈里仍然可见。
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= ensureBucketMaxAttempts; attempt++) {
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
                if (lastFailure != null) {
                    e.addSuppressed(lastFailure);
                }
                lastFailure = e;
                if (attempt < ensureBucketMaxAttempts) {
                    // 日志里直接打毫秒数，不要换算成秒——换算成秒再用整数除法会把
                    // 500ms 这种配置值截断显示成"0 秒后重试"，跟代码实际的 sleep
                    // 时长对不上，误导照着日志排查 MinIO 就绪窗口的人。
                    log.warn("连接 MinIO 确认 bucket 失败（第 {}/{} 次尝试，{} 毫秒后重试）：bucket={}, 原因={}",
                            attempt, ensureBucketMaxAttempts, ensureBucketRetryDelayMillis, bucket, e.getMessage());
                    sleepBeforeRetry(lastFailure);
                }
            }
        }
        throw new IllegalStateException(
                "Failed to ensure MinIO bucket exists after " + ensureBucketMaxAttempts + " attempts: " + bucket,
                lastFailure);
    }

    private void sleepBeforeRetry(Exception lastFailure) {
        try {
            Thread.sleep(ensureBucketRetryDelayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            // 中断时也要带上目前为止真正的 MinIO 失败原因，而不是只报
            // "被中断了"——不然排查的人看不出中断之前到底是什么在失败。
            throw new IllegalStateException("Interrupted while retrying MinIO bucket check: " + bucket, lastFailure);
        }
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
