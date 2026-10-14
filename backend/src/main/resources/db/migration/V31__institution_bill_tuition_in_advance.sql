-- 产品反馈：部分机构月初预收下个月托管费、同时后收上个月实际发生的餐费/课外课费
-- （两者合并成一张账单，不是两次独立收款）。这个字段控制生成账单时，餐费/课外课费/
-- 请假统计要不要往前倒一个月去取数据；托管费本身是固定月租，不受影响。默认关闭，
-- 已有机构行为不变。
ALTER TABLE institution ADD COLUMN bill_tuition_in_advance BOOLEAN NOT NULL DEFAULT FALSE;
