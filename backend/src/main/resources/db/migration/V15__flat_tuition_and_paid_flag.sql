-- 托管费从"按天计费"改为"按月固定金额"。试点机构已配置真实的按天单价，
-- 直接改名会让旧单价被误当作月费金额使用，因此先清空现有配置，
-- 强制管理员在改造后手动重新录入正确的月度金额。
DELETE FROM class_billing_rate;

ALTER TABLE class_billing_rate
    CHANGE COLUMN tuition_rate_per_day tuition_rate_per_month DECIMAL(10, 2) NOT NULL;

ALTER TABLE monthly_bill
    ADD COLUMN is_paid BOOLEAN NOT NULL DEFAULT FALSE AFTER total_amount;
