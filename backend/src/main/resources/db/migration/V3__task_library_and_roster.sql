CREATE TABLE task_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    subject VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_template_institution FOREIGN KEY (institution_id) REFERENCES institution(id)
);

CREATE TABLE class_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_class_room_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_class_room_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id)
);

CREATE TABLE student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    school_class_name VARCHAR(50) NOT NULL,
    enrolled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_student_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id)
);
