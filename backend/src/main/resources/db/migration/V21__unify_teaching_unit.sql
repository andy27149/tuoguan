-- 托管班（class_room, 按月计费）与课外课程（course, 按课时计费）合并为统一的 teaching_unit 表，
-- 用 billing_mode 区分两种计费模式。两表原主键都从 1 自增、空间重叠，合并时课外课程侧的 id
-- 统一加固定偏移量 1000000000 避免与托管班 id 冲突；托管班侧保留原 id 不变。
-- 纯结构迁移，不做业务数据正确性修复——历史数据由机构管理方手动核对处理。

CREATE TABLE teaching_unit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    billing_mode ENUM('MONTHLY', 'LESSON_COUNT') NOT NULL,
    lesson_duration_minutes INT NULL,
    price_per_lesson DECIMAL(10, 2) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teaching_unit_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_teaching_unit_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id)
);

INSERT INTO teaching_unit (id, institution_id, teacher_id, name, billing_mode, lesson_duration_minutes,
                            price_per_lesson, active, created_at)
SELECT id, institution_id, teacher_id, name, 'MONTHLY', NULL, NULL, TRUE, created_at
FROM class_room;

INSERT INTO teaching_unit (id, institution_id, teacher_id, name, billing_mode, lesson_duration_minutes,
                            price_per_lesson, active, created_at)
SELECT id + 1000000000, institution_id, teacher_id, name, 'LESSON_COUNT', lesson_duration_minutes,
       price_per_lesson, active, created_at
FROM course;

-- student：class_room_id -> teaching_unit_id（值不变，托管班 id 未偏移）
ALTER TABLE student DROP FOREIGN KEY fk_student_class_room;
ALTER TABLE student CHANGE COLUMN class_room_id teaching_unit_id BIGINT NULL;
ALTER TABLE student
    ADD CONSTRAINT fk_student_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

-- 五张单归属考勤表：class_room_id -> teaching_unit_id（值不变）
ALTER TABLE daily_task DROP FOREIGN KEY fk_daily_task_class_room;
ALTER TABLE daily_task CHANGE COLUMN class_room_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE daily_task
    ADD CONSTRAINT fk_daily_task_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

ALTER TABLE class_dismissal DROP FOREIGN KEY fk_class_dismissal_class_room;
ALTER TABLE class_dismissal CHANGE COLUMN class_room_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE class_dismissal
    ADD CONSTRAINT fk_class_dismissal_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

ALTER TABLE student_daily_note DROP FOREIGN KEY fk_student_daily_note_class_room;
ALTER TABLE student_daily_note CHANGE COLUMN class_room_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE student_daily_note
    ADD CONSTRAINT fk_student_daily_note_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

ALTER TABLE student_arrival_checkin DROP FOREIGN KEY fk_student_pickup_checkin_class_room;
ALTER TABLE student_arrival_checkin CHANGE COLUMN class_room_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE student_arrival_checkin
    ADD CONSTRAINT fk_student_arrival_checkin_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

ALTER TABLE student_leave_record DROP FOREIGN KEY fk_student_leave_record_class_room;
ALTER TABLE student_leave_record CHANGE COLUMN class_room_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE student_leave_record
    ADD CONSTRAINT fk_student_leave_record_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

-- 托管定价：class_room_id -> teaching_unit_id（值不变）
ALTER TABLE class_billing_rate DROP FOREIGN KEY fk_class_billing_rate_class_room;
ALTER TABLE class_billing_rate CHANGE COLUMN class_room_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE class_billing_rate
    ADD CONSTRAINT fk_class_billing_rate_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

-- 账单：class_room_id -> teaching_unit_id，且改为可空，让纯课外课学生也能生成账单
ALTER TABLE monthly_bill DROP FOREIGN KEY fk_monthly_bill_class_room;
ALTER TABLE monthly_bill CHANGE COLUMN class_room_id teaching_unit_id BIGINT NULL;
ALTER TABLE monthly_bill
    ADD CONSTRAINT fk_monthly_bill_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

-- 课外课报名/消课/充值：course_id -> teaching_unit_id，值加偏移量 1000000000 对应新 id
RENAME TABLE student_course_enrollment TO student_unit_enrollment;
ALTER TABLE student_unit_enrollment DROP FOREIGN KEY fk_enrollment_course;
UPDATE student_unit_enrollment SET course_id = course_id + 1000000000;
ALTER TABLE student_unit_enrollment CHANGE COLUMN course_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE student_unit_enrollment
    ADD CONSTRAINT fk_unit_enrollment_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

ALTER TABLE course_consumption_record DROP FOREIGN KEY fk_consumption_course;
UPDATE course_consumption_record SET course_id = course_id + 1000000000;
ALTER TABLE course_consumption_record CHANGE COLUMN course_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE course_consumption_record
    ADD CONSTRAINT fk_consumption_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

ALTER TABLE course_recharge_record DROP FOREIGN KEY fk_recharge_course;
UPDATE course_recharge_record SET course_id = course_id + 1000000000;
ALTER TABLE course_recharge_record CHANGE COLUMN course_id teaching_unit_id BIGINT NOT NULL;
ALTER TABLE course_recharge_record
    ADD CONSTRAINT fk_recharge_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id);

DROP TABLE class_room;
DROP TABLE course;
