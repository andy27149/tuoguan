package com.tuoguan.backend.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class StorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public StorageService(MinioClient minioClient, String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure MinIO bucket exists: " + bucket, e);
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
