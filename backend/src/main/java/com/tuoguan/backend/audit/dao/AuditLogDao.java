package com.tuoguan.backend.audit.dao;

public interface AuditLogDao {

    void insert(Long institutionId, Long actorTeacherId, String action, String targetType, Long targetId,
                String detail);
}
