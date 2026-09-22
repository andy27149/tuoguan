CREATE TABLE class_billing_rate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    tuition_rate_per_day DECIMAL(10,2) NOT NULL,
    meal_rate_per_day DECIMAL(10,2) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_class_billing_rate_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_class_billing_rate_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id),
    CONSTRAINT uq_class_billing_rate_class_room UNIQUE (class_room_id)
);

CREATE TABLE student_extra_fee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    amount_per_month DECIMAL(10,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_extra_fee_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_student_extra_fee_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT uq_student_extra_fee_name UNIQUE (student_id, name)
);

CREATE TABLE student_leave_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    leave_date DATE NOT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_leave_record_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_student_leave_record_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_student_leave_record_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id),
    CONSTRAINT uq_student_leave_record_date UNIQUE (student_id, leave_date)
);

CREATE TABLE monthly_bill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    class_room_id BIGINT NOT NULL,
    bill_month VARCHAR(7) NOT NULL,
    total_weekdays INT NOT NULL,
    leave_days INT NOT NULL,
    attendance_days INT NOT NULL,
    tuition_amount DECIMAL(10,2) NOT NULL,
    meal_amount DECIMAL(10,2) NOT NULL,
    extra_fee_total DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_monthly_bill_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_monthly_bill_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_monthly_bill_class_room FOREIGN KEY (class_room_id) REFERENCES class_room(id),
    CONSTRAINT uq_monthly_bill_student_month UNIQUE (student_id, bill_month)
);

CREATE TABLE monthly_bill_extra_fee_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    monthly_bill_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_monthly_bill_extra_fee_line_bill FOREIGN KEY (monthly_bill_id) REFERENCES monthly_bill(id) ON DELETE CASCADE
);
