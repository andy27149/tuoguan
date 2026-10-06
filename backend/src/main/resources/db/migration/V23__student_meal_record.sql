-- 老师每天标记某个学生是否用餐：有记录就是吃了，删记录就是撤销，设计风格
-- 参照 student_arrival_checkin/student_leave_record。
CREATE TABLE student_meal_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    teaching_unit_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    meal_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_meal_record_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_student_meal_record_teaching_unit FOREIGN KEY (teaching_unit_id) REFERENCES teaching_unit(id),
    CONSTRAINT fk_student_meal_record_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT uq_student_meal_record_student_date UNIQUE (student_id, meal_date)
);

-- 生成账单时把本月实际用餐的具体日期拍成快照，账单生成后老师再改动用餐记录
-- 不会让已出的账单跟着变，仿照 monthly_bill_extra_fee_line 的模式。
CREATE TABLE monthly_bill_meal_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    monthly_bill_id BIGINT NOT NULL,
    meal_date DATE NOT NULL,
    CONSTRAINT fk_monthly_bill_meal_line_bill FOREIGN KEY (monthly_bill_id) REFERENCES monthly_bill(id) ON DELETE CASCADE
);
