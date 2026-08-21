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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

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
}
