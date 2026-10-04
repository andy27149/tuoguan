package com.tuoguan.backend.audit.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditLogDao implements AuditLogDao {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditLogDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(Long institutionId, Long actorTeacherId, String action, String targetType, Long targetId,
                        String detail) {
        jdbcTemplate.update(
                "INSERT INTO audit_log (institution_id, actor_teacher_id, action, target_type, target_id, detail) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                institutionId, actorTeacherId, action, targetType, targetId, detail);
    }
}
