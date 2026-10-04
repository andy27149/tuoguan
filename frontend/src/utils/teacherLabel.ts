// 教师登记的姓名本身可能已经是"王老师"这类称呼（而不是"王伟"这样的正式姓名），
// 不能无条件再拼接"老师"后缀，否则会显示成"王老师老师"。
export function teacherLabel(teacherName: string): string {
  return teacherName.endsWith('老师') ? teacherName : `${teacherName}老师`
}
