-- 产品诊断 #54：账单生成时如果某笔课外课附加费已经通过账单生成之后、但该月账单还没
-- 标记已缴费之前的补充值抵消，这一行的应收金额会被改成 0（或差额），这个标记位让前端
-- 知道要换一种说法展示（"已通过后续充值补齐，不收费"），而不是继续显示对不上账的
-- "N次 × 单价 = 金额"换算。
ALTER TABLE monthly_bill_extra_fee_line
    ADD COLUMN settled_by_recharge BOOLEAN NOT NULL DEFAULT FALSE;
