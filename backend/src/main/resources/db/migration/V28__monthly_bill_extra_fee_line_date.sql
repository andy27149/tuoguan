-- 生成账单时把当月计入"课外课附加费"的具体消课日期拍成快照（产品反馈 #51：账单明细
-- 只有课时数和金额，没有展示具体哪天上了什么课），仿照 monthly_bill_meal_line 的模式——
-- 账单生成后老师再补/改消课记录不会让已出的账单跟着变。
CREATE TABLE monthly_bill_extra_fee_line_date (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    extra_fee_line_id BIGINT NOT NULL,
    consumption_date DATE NOT NULL,
    CONSTRAINT fk_monthly_bill_extra_fee_line_date_line
        FOREIGN KEY (extra_fee_line_id) REFERENCES monthly_bill_extra_fee_line(id) ON DELETE CASCADE
);
