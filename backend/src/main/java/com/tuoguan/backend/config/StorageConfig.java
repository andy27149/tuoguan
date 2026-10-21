package com.tuoguan.backend.config;

import com.tuoguan.backend.storage.StorageService;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                    @Value("${minio.access-key}") String accessKey,
                                    @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public StorageService storageService(MinioClient minioClient,
                                          @Value("${minio.bucket}") String bucket,
                                          @Value("${minio.startup-retry.max-attempts}") int startupRetryMaxAttempts,
                                          @Value("${minio.startup-retry.delay-millis}") long startupRetryDelayMillis) {
        return new StorageService(minioClient, bucket, startupRetryMaxAttempts, startupRetryDelayMillis);
    }
}
