CREATE TABLE student_daily_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    note_date DATE NOT NULL,
    rating SMALLINT NOT NULL DEFAULT 0,
    comment VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_daily_note_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_student_daily_note_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id),
    CONSTRAINT fk_student_daily_note_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT uq_student_daily_note_student_date UNIQUE (student_id, note_date)
);
