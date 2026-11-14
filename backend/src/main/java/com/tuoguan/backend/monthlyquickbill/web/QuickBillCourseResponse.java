package com.tuoguan.backend.monthlyquickbill.web;

// consumptionCount：这个学生这门课这个月已经有的消课记录数（不论是这个工具自己写的还是
// 老师真实点名消课写的）。>0 时前端应该把这门课的输入框锁成只读——重新提交会被
// MonthlyQuickBillService 的"已提交过"校验拒绝，提前在界面上锁住，避免运营人员改了
// 这一格之后，连带这个学生这次提交里本来能成功的用餐/请假也一起失败（同一个学生的
// 提交是一个事务）。
public record QuickBillCourseResponse(Long courseId, String name, int consumptionCount) {
}
