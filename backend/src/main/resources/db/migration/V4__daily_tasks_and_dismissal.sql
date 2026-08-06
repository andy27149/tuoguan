CREATE TABLE daily_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    task_date DATE NOT NULL,
    task_template_id BIGINT NULL,
    subject VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_custom BOOLEAN NOT NULL DEFAULT FALSE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_daily_task_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_daily_task_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id),
    CONSTRAINT fk_daily_task_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_daily_task_task_template FOREIGN KEY (task_template_id) REFERENCES task_template(id)
);

CREATE TABLE class_dismissal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    dismissal_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_class_dismissal_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_class_dismissal_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id),
    CONSTRAINT uq_class_dismissal_class_date UNIQUE (class_room_id, dismissal_date)
);
