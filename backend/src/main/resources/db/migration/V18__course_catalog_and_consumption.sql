-- 课程目录 + 课外消课/充值账户。学生可以不再属于任何托管班（纯课外学生），
-- class_room_id 从 NOT NULL 改为可空；现有外键无 ON DELETE 子句，改可空不影响约束。
ALTER TABLE student
    MODIFY COLUMN class_room_id BIGINT NULL;

-- 纯课外学生由负责教师自助添加，可能没有对应的学校班级信息，school_class_name 一并改为可空。
ALTER TABLE student
    MODIFY COLUMN school_class_name VARCHAR(50) NULL;

CREATE TABLE course (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_per_lesson DECIMAL(10, 2) NULL,
    lesson_duration_minutes INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id)
);

CREATE TABLE student_course_enrollment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_enrollment_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT uq_enrollment_student_course UNIQUE (student_id, course_id)
);

-- 同日重复消课是合法的业务动作（用户确认后可再次记一节课），故意不加日期唯一约束，
-- 由服务层负责重复提醒与二次确认。price_snapshot 固化下单时的课程单价，避免后续调价影响历史账单。
CREATE TABLE course_consumption_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    consumption_date DATE NOT NULL,
    price_snapshot DECIMAL(10, 2) NOT NULL,
    recorded_by_teacher_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consumption_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_consumption_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_consumption_course FOREIGN KEY (course_id) REFERENCES course(id),
    CONSTRAINT fk_consumption_teacher FOREIGN KEY (recorded_by_teacher_id) REFERENCES teacher(id)
);

-- 纯课外学生的预充值余额：不存储余额列，余额 = SUM(recharge.amount) - SUM(consumption.price_snapshot)，
-- 实时计算以避免并发写入导致的漂移。允许为负（超额消课）。
CREATE TABLE course_recharge_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    note VARCHAR(255) NULL,
    recorded_by_teacher_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recharge_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_recharge_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_recharge_teacher FOREIGN KEY (recorded_by_teacher_id) REFERENCES teacher(id)
);

-- 旧的"手工课外费"机制被课程消课记录取代。用户已授权清空生产数据，直接废弃。
DROP TABLE student_extra_fee_lesson_count;
DROP TABLE student_extra_fee;
