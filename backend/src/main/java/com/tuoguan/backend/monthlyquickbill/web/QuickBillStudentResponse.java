package com.tuoguan.backend.monthlyquickbill.web;

import java.util.List;

// mealDays/leaveDays：这个学生"这期账单实际结算的那个月"（见 MonthlyQuickBillService 对
// 提前收机构的月份换算）已经保存过的用餐/请假天数——运营人员可能分几次陆续录入（比如
// 月中录一半、月底再录剩下的），界面要把这两个数字预填进输入框，让他们在已有基础上改
// 成新的"整月累计数"再保存，而不是凭记忆口算累计值、一不小心把之前存的覆盖掉。
public record QuickBillStudentResponse(Long studentId, String name, int mealDays, int leaveDays,
                                        List<QuickBillCourseResponse> courses) {
}
