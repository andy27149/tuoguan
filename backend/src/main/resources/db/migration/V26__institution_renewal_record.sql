CREATE TABLE institution_renewal_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    plan VARCHAR(20) NOT NULL,
    amount DECIMAL(10,2) NULL,
    valid_until DATE NOT NULL,
    note VARCHAR(255) NULL,
    recorded_by_teacher_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_institution_renewal_record_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_institution_renewal_record_teacher FOREIGN KEY (recorded_by_teacher_id) REFERENCES teacher(id)
);
