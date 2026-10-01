import type { StudentCourseStatement } from '../api/course'

interface CourseStatementViewProps {
  statement: StudentCourseStatement
}

export function CourseStatementView({ statement }: CourseStatementViewProps) {
  const logEntries = [
    ...statement.consumptions.map((c) => ({
      date: c.consumptionDate,
      label: `${c.courseName ?? '课程'} · ${c.teacherName ? `${c.teacherName}老师` : '老师'}`,
      detail: '消课 1 课时',
      kind: 'consumption' as const,
    })),
    ...statement.recharges.map((r) => ({
      date: r.createdAt.slice(0, 10),
      label: `${r.courseName ?? '课程'}${r.note ? ` · ${r.note}` : ''}`,
      detail: `充值 +${r.lessonCount} 课时`,
      kind: 'recharge' as const,
    })),
  ].sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0))

  return (
    <div className="course-statement">
      <ul className="course-statement__balances">
        {statement.balances.map((b) => (
          <li key={b.courseId} className="course-statement__balance-row">
            <span className="course-statement__balance-name">{b.courseName ?? `课程#${b.courseId}`}</span>
            <span
              className={
                b.balance < 0
                  ? 'course-statement__balance-value course-statement__balance-value--negative'
                  : 'course-statement__balance-value'
              }
            >
              剩 {b.balance} 课时
            </span>
          </li>
        ))}
        {statement.balances.length === 0 && <li className="course-statement__empty">暂无课程记录</li>}
      </ul>

      <p className="course-statement__section-title">消课与充值明细</p>
      <ul className="course-statement__log">
        {logEntries.map((entry, idx) => (
          <li key={idx} className="course-statement__log-row">
            <span className="course-statement__log-date">{entry.date}</span>
            <span className="course-statement__log-label">{entry.label}</span>
            <span
              className={
                entry.kind === 'recharge'
                  ? 'course-statement__log-detail course-statement__log-detail--recharge'
                  : 'course-statement__log-detail'
              }
            >
              {entry.detail}
            </span>
          </li>
        ))}
        {logEntries.length === 0 && <li className="course-statement__empty">暂无记录</li>}
      </ul>
    </div>
  )
}
