export interface ProductUpdate {
  /** 用日期做 id：新条目日期必须递增，用于跟 localStorage 里记录的"已读到哪条"比较。 */
  id: string
  date: string
  items: string[]
}

// 新条目加在最前面，id 用当天日期（YYYY-MM-DD）。
export const PRODUCT_UPDATES: ProductUpdate[] = [
  {
    id: '2026-10-04',
    date: '2026-10-04',
    items: [
      '修复学籍班未填写时，编辑学生会白屏、批量分配任务按钮无法点击的问题',
      '停用课程/班级、调整缴费状态、批量设置定价、放学、移出花名册等操作补充了二次确认，避免误触',
      '教师端任务删除按钮加大了点击区域，不再容易误触旁边的勾选按钮',
    ],
  },
]
