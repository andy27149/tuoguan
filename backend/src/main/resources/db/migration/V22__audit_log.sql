-- 最小版操作审计日志：只记录"谁在什么时候对什么资源做了什么高风险操作"，
-- 不提供查询/展示 UI（见产品诊断：操作日志缺失，用于未来纠纷排查有据可查）。
CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    actor_teacher_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NULL,
    detail VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_log_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_teacher_id) REFERENCES teacher(id)
);

CREATE INDEX idx_audit_log_institution_created ON audit_log(institution_id, created_at);
