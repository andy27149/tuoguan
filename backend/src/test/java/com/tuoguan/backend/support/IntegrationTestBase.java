package com.tuoguan.backend.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    // 2026-10-15 起 MinIO 官方锁死了 minio/minio 在 Docker Hub 的匿名拉取权限
    // （docker-compose.yml 里有更详细的说明），改用我们自己镜像到 GHCR 的那一份——
    // MinIOContainer 的单字符串构造函数内部会校验镜像名必须和官方 "minio/minio"
    // 兼容，直接传自定义仓库路径会被拒绝，必须显式 asCompatibleSubstituteFor 声明。
    static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("ghcr.io/andy27149/tuoguan-minio:latest").asCompatibleSubstituteFor("minio/minio"));

    static {
        MYSQL.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void registerMinioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", MINIO::getS3URL);
        registry.add("minio.access-key", MINIO::getUserName);
        registry.add("minio.secret-key", MINIO::getPassword);
        registry.add("minio.bucket", () -> "avatars-test");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    // 诊断 #45/#53：Testcontainers 的 MySQL 实例是整个测试进程里所有测试类共享的单例
    // （见上面 static 字段），历史上多个测试类各自手写硬编码手机号段，只要两个类凑巧
    // 用了同一个号，全量跑测试时就会撞上 teacher.uq_teacher_phone 唯一键报错——这正是
    // 之前反复出现的 flaky 测试根因。新建号的测试统一调用这个方法拿号，"199"前缀在全仓库
    // 现有硬编码号段（138/139 开头）之外，天然不会撞号；多线程并行跑测试时 AtomicLong
    // 也保证不会发出重复号。
    private static final AtomicLong PHONE_SEQUENCE = new AtomicLong(1);

    protected static String uniquePhone() {
        return "199" + String.format("%08d", PHONE_SEQUENCE.getAndIncrement());
    }
}
