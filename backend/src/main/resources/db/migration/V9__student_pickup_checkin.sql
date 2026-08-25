CREATE TABLE student_pickup_checkin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    picked_up_by VARCHAR(100) NOT NULL DEFAULT '',
    picked_up_at VARCHAR(5) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_pickup_checkin_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_student_pickup_checkin_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id),
    CONSTRAINT fk_student_pickup_checkin_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT uq_student_pickup_checkin_student_date UNIQUE (student_id, checkin_date)
);
