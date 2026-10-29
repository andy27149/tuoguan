-- 纯课外课学生（没有托管班）此前完全没有用餐记录的入口/概念——这张表承载课外课老师
-- 在点名消课页面顺手登记的"今天这个孩子吃没吃饭"，按学生+日期唯一（不挂具体哪门课），
-- 因为"吃没吃饭"是按天的事实，不是按课程的事实；一天内任何一门课的老师先记上就算数，
-- 不会被其它课程重复计费。
CREATE TABLE course_meal_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    institution_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    meal_date DATE NOT NULL,
    recorded_by_teacher_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_course_meal_record_institution FOREIGN KEY (institution_id) REFERENCES institution(id),
    CONSTRAINT fk_course_meal_record_student FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT fk_course_meal_record_teacher FOREIGN KEY (recorded_by_teacher_id) REFERENCES teacher(id),
    CONSTRAINT uq_course_meal_record_student_date UNIQUE (student_id, meal_date)
);

-- 纯课外课学生没有托管班，自然也没有 ClassBillingRate 可用，这笔餐费按机构统一配置
-- 一个单价（不按课程/托管班单独配置）——机构内部怎么分摊这笔钱是机构自己的事，系统
-- 只负责把这笔钱算进月度账单。为 NULL 表示机构还没配置，generateBill 里只有当月
-- 真的产生了用餐记录时才会要求这个值必须存在。
ALTER TABLE institution ADD COLUMN off_campus_meal_rate_per_day DECIMAL(10,2) NULL;
