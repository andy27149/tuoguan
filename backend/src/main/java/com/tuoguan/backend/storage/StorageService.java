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
    // 两个重试参数是从 application.yml/环境变量读的，就是为了方便以后调，但
    // "方便调"也意味着容易被误配成一个夸张的数字（比如把 delay-millis 的单位
    // 当成秒填、或者 max-attempts 多打一个 0）——真出现这种配置失误时，应该让它在
    // 启动阶段就快速、明确地报错，而不是让 Spring 容器初始化卡住一段不合理的时长
    // 才报错，甚至在 delay-millis=0 时完全不卡但疯狂重试。这里要分开设两个独立的
    // 上限，而不是像第一版那样直接把两个参数相乘当成"总等待时间"校验——那个写法
    // 有两个真实 bug（已经在 review 里实测验证过）：
    //   1. delay-millis=0 时，不管 max-attempts 多大，乘积永远是 0，完全绕过上限，
    //      而这恰好是"疯狂重试、瞬间打爆 MinIO"这种最危险的配置
    //   2. max-attempts=1 时循环只会尝试一次、根本不会走到 sleep 这一步，但哪怕
    //      delay-millis 配置得再大也会被乘积公式误判超限而拒绝——其实这是个完全
    //      安全的配置
    // 正确的账应该按实际会睡多少次来算：N 次尝试之间只会 sleep (N-1) 次（见下面
    // ensureBucketExists 里 `attempt < ensureBucketMaxAttempts` 这个判断），所以
    // 睡眠总预算的上限要用 (max-attempts - 1) * delay-millis，而次数本身要单独
    // 设一个跟 delay-millis 无关的上限，堵住"次数极大、间隔为 0"这个漏洞。
    //
    // 这两个上限管的只是"主动 sleep 的时间"和"尝试次数"，不等于整个启动过程的
    // 严格上限——每次 minioClient.bucketExists() 本身的网络调用（连接超时、读
    // 超时）也会占用真实时间，这里没有也没必要去精确折算 MinIO SDK/OkHttp 默认
    // 超时值再反推一个"绝对不超过 N 分钟"的硬保证，纯粹是为了在"配置明显离谱"
    // 这种容易犯的错误上快速报错，不是对总启动时长的精确数学保证。
    private static final int MAX_ATTEMPTS_CEILING = 1000;
    private static final long MAX_TOTAL_SLEEP_BUDGET_MILLIS = 300_000; // 5 分钟（仅指主动 sleep 的时间）

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
        if (ensureBucketMaxAttempts > MAX_ATTEMPTS_CEILING) {
            throw new IllegalArgumentException(
                    "minio.startup-retry.max-attempts (" + ensureBucketMaxAttempts + ") exceeds the sanity cap of "
                            + MAX_ATTEMPTS_CEILING + "; check for a typo'd extra digit.");
        }
        // 真正会睡眠的次数是 (max-attempts - 1)，不是 max-attempts——最后一次尝试
        // 失败后直接往下走到最终的 throw，不会再 sleep。用 long 相乘，避免误写成
        // 两个 int 相乘在乘法本身那一步就先溢出。
        long totalSleepBudgetMillis = (long) (ensureBucketMaxAttempts - 1) * ensureBucketRetryDelayMillis;
        if (totalSleepBudgetMillis > MAX_TOTAL_SLEEP_BUDGET_MILLIS) {
            throw new IllegalArgumentException(
                    "minio.startup-retry.max-attempts (" + ensureBucketMaxAttempts + " - 1) * delay-millis ("
                            + ensureBucketRetryDelayMillis + ") = " + totalSleepBudgetMillis
                            + "ms exceeds the sanity cap of " + MAX_TOTAL_SLEEP_BUDGET_MILLIS
                            + "ms; check for a unit mix-up (seconds vs. milliseconds) or a typo'd extra digit.");
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
            // cause 带上目前为止真正的 MinIO 失败原因，而不是只报"被中断了"——不然
            // 排查的人看不出中断之前到底是什么在失败；中断本身（在哪个瞬间、什么
            // 线程被谁打断）也不该整个丢掉，挂成 suppressed，两条线索都留在堆栈里。
            IllegalStateException ex = new IllegalStateException(
                    "Interrupted while retrying MinIO bucket check: " + bucket, lastFailure);
            ex.addSuppressed(interrupted);
            throw ex;
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
