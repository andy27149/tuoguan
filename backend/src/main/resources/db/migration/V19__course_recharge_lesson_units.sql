-- 课外课充值与消课统一按"课时"计量，不再按金额计量：机构卖的是课程课时包（如"10节书法课"），
-- 不同课程单价不同，混算成金额对纯课外机构没有意义；充值因此改为与具体课程绑定、按课时数计数。
-- 该机制刚随 V18 上线，纯课外机构尚未实际产生充值数据，直接清空重建列结构，无需迁移历史数据。
DELETE FROM course_recharge_record;

ALTER TABLE course_recharge_record
    ADD COLUMN course_id BIGINT NOT NULL AFTER student_id,
    ADD COLUMN lesson_count INT NOT NULL AFTER course_id,
    DROP COLUMN amount,
    ADD CONSTRAINT fk_recharge_course FOREIGN KEY (course_id) REFERENCES course(id);
