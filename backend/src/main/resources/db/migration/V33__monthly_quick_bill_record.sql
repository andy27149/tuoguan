-- 月度批量录入工具合成的到校/用餐/请假记录，需要精确知道"这个学生这一天的记录是不是
-- 本工具自己写的"，才能在重新提交时只清掉自己写的、不碰真实数据。靠猜（比如固定到校
-- 时间/固定请假原因）不安全——live测试时就真的踩到过：机构种子数据里凑巧也用了同一个
-- 到校时间字符串，导致一条真实记录被误删。这张表只记"日期+类型"，是唯一可信的来源。
CREATE TABLE monthly_quick_bill_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    record_type VARCHAR(20) NOT NULL,
    record_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_monthly_quick_bill_record_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_monthly_quick_bill_record_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT uq_monthly_quick_bill_record_student_date_type UNIQUE (student_id, record_date, record_type)
);
