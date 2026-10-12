CREATE TABLE student_share_view_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    view_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_share_view_log_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_student_share_view_log_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT uq_student_share_view_log_student_date UNIQUE (student_id, view_date)
);
