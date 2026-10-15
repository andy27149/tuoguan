package com.tuoguan.backend.health;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

// 产品诊断 #61：此前这里硬编码返回 {"status":"ok"}，不探测任何依赖——数据库连不上、
// MinIO 挂了，这个接口照样答"ok"，docker-compose healthcheck 和人工巡检都无法通过它
// 发现"服务名存实亡"的情况。现在真实探测 DB/MinIO 连通性；任一依赖不可用时返回 503，
// 配合 docker-compose.yml 给 backend 新增的 healthcheck 使用。
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;
    private final MinioClient minioClient;
    private final String minioBucket;

    public HealthController(DataSource dataSource, MinioClient minioClient,
                             @Value("${minio.bucket}") String minioBucket) {
        this.dataSource = dataSource;
        this.minioClient = minioClient;
        this.minioBucket = minioBucket;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> result = new LinkedHashMap<>();
        boolean dbOk = checkDatabase();
        boolean storageOk = checkStorage();
        result.put("database", dbOk ? "ok" : "down");
        result.put("storage", storageOk ? "ok" : "down");
        result.put("status", dbOk && storageOk ? "ok" : "degraded");
        HttpStatus status = dbOk && storageOk ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(result);
    }

    private boolean checkDatabase() {
        // 2 秒超时：健康检查本身不应该比它要探测的依赖更拖慢请求链路。
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            log.warn("Health check: database connection failed", e);
            return false;
        }
    }

    private boolean checkStorage() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
        } catch (Exception e) {
            log.warn("Health check: MinIO connection failed", e);
            return false;
        }
    }
}
