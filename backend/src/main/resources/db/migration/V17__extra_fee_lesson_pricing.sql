-- 课外费从"固定月费"改为"单价 × 本月上课数"计费。旧的月费金额与新语义
-- （每节课单价）含义不同，直接改名会被误当作单价使用，参照 V15 的处理方式，
-- 清空现有配置，强制管理员按新规则重新录入。
DELETE FROM student_extra_fee;

ALTER TABLE student_extra_fee
    CHANGE COLUMN amount_per_month price_per_lesson DECIMAL(10, 2) NOT NULL;

CREATE TABLE student_extra_fee_lesson_count (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_extra_fee_id BIGINT NOT NULL,
    bill_month VARCHAR(7) NOT NULL,
    lesson_count INT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_extra_fee_lesson_count_fee FOREIGN KEY (student_extra_fee_id) REFERENCES student_extra_fee(id) ON DELETE CASCADE,
    CONSTRAINT uq_extra_fee_lesson_count_month UNIQUE (student_extra_fee_id, bill_month)
);

ALTER TABLE monthly_bill_extra_fee_line
    ADD COLUMN price_per_lesson DECIMAL(10, 2) NULL AFTER name,
    ADD COLUMN lesson_count INT NULL AFTER price_per_lesson;
