import type { CourseActivityRow } from '../api/publicShare'

interface CourseActivityViewProps {
  rows: CourseActivityRow[]
}

// 托管班学生同时报名课外课时的轻量展示：只列课程+最近消课日期，不带充值/余额
// （这类学生的课外课费用走托管月度账单附加费，余额概念不成立）。见 CourseStatementView。
export function CourseActivityView({ rows }: CourseActivityViewProps) {
  return (
    <div className="course-activity">
      <p className="course-activity__section-title">课外课情况</p>
      <ul className="course-activity__list">
        {rows.map((row) => (
          <li key={row.courseId} className="course-activity__row">
            <span className="course-activity__name">{row.courseName ?? `课程#${row.courseId}`}</span>
            <span className="course-activity__dates">
              {row.recentConsumptionDates.length > 0 ? row.recentConsumptionDates.join('、') : '暂无消课记录'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
