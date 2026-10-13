-- 产品诊断 #14：账单标记"已缴费"时记录收款方式，方便月末按渠道对账（现金/微信/
-- 支付宝/银行转账）。可空、无默认值——未缴费账单为 NULL；取消缴费时也清空。不回填
-- 历史数据，之前已缴费的账单本来就没人记录过收款方式。
ALTER TABLE monthly_bill ADD COLUMN payment_method VARCHAR(20) NULL AFTER is_paid;
