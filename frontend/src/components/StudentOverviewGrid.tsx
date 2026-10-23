import type { Student } from '../api/students'

interface StudentOverviewGridProps {
  students: Student[]
  arrivalByStudent: Map<number, string>
  mealByStudent: Set<number>
  leaveByStudent: Map<number, string>
  onSelect: (studentId: number) => void
}

// 大班级（>10人）默认视图：一人一个头像+姓名的紧凑网格，状态靠头像外圈颜色和
// 用餐角标区分，不展示任务/评分这些细节——要操作或者看详情，点头像弹出来。
// 到了/请假互斥（后端打卡到了会联动清掉请假记录，反之亦然），用餐只在到了之后
// 才可能发生，所以外圈颜色三选一、用餐角标是独立叠加的信号，不会同时出现到了
// 和请假两种颜色。
export function StudentOverviewGrid({
  students,
  arrivalByStudent,
  mealByStudent,
  leaveByStudent,
  onSelect,
}: StudentOverviewGridProps) {
  return (
    <div className="overview-grid">
      {students.map((student) => {
        const arrivedAt = arrivalByStudent.get(student.id)
        const leaveReason = leaveByStudent.get(student.id)
        const hasMeal = mealByStudent.has(student.id)
        const status = leaveReason ? 'leave' : arrivedAt ? 'arrived' : 'default'
        return (
          <button
            key={student.id}
            type="button"
            className="student-chip"
            data-status={status}
            onClick={() => onSelect(student.id)}
          >
            <span className="chip-avatar-wrap">
              <span className="chip-avatar" aria-hidden="true">
                {student.avatarUrl ? <img src={student.avatarUrl} alt="" /> : student.name.slice(0, 1)}
              </span>
              {hasMeal && (
                <span className="chip-meal-badge" aria-hidden="true">
                  🍚
                </span>
              )}
            </span>
            <span className="chip-name">{student.name}</span>
          </button>
        )
      })}
    </div>
  )
}
