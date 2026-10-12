import type { CourseActivityRow } from '../api/publicShare'

interface CourseActivityViewProps {
  rows: CourseActivityRow[]
  selectedDate: string | null
}

// 托管班学生同时报名课外课时的轻量展示：只展示当前选中日期上过的课外课，不带充值/余额
// （这类学生的课外课费用走托管月度账单附加费，余额概念不成立）。见 CourseStatementView。
export function CourseActivityView({ rows, selectedDate }: CourseActivityViewProps) {
  const matchingRows = selectedDate ? rows.filter((row) => row.recentConsumptionDates.includes(selectedDate)) : []

  return (
    <div className="course-activity">
      <p className="course-activity__section-title">课外课情况</p>
      {matchingRows.length === 0 ? (
        <p className="course-activity__empty">当天没有课外课记录</p>
      ) : (
        <ul className="course-activity__list">
          {matchingRows.map((row) => (
            <li key={row.courseId} className="course-activity__row">
              <span className="course-activity__name">{row.courseName ?? `课程#${row.courseId}`}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
