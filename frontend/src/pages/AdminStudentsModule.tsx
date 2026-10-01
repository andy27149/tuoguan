import { useEffect, useState } from 'react'
import * as courseApi from '../api/course'
import { AdminCourseStatementModal } from '../components/AdminCourseStatementModal'

export function AdminStudentsModule() {
  const [students, setStudents] = useState<courseApi.AdminStudent[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [statementStudent, setStatementStudent] = useState<{ id: number; name: string } | null>(null)

  useEffect(() => {
    courseApi
      .fetchAdminStudents()
      .then(setStudents)
      .catch(() => setLoadError('加载学生列表失败，请刷新重试'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="space-y-4 p-4">
      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">学生总览（{students.length}）</h2>
        {loadError && <p className="mt-2 text-sm text-[#b7591f]">{loadError}</p>}
        {loading && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loading && (
          <table className="mt-3 w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">姓名</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">学籍班</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管班</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">身份</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {students.map((student) => (
                <tr key={student.id} className="border-b border-[#ece7de] align-middle hover:bg-[#faf7ff]">
                  <td className="px-4 py-3">
                    <span className="font-medium text-[#241f3d]">{student.name}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-[#7c7391]">{student.schoolClassName || '—'}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-[#7c7391]">{student.classRoomName || '—'}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        student.offCampusOnly
                          ? 'rounded-full bg-[#fdf1e6] px-2 py-0.5 text-xs text-[#b7591f]'
                          : 'rounded-full bg-[#e9f7f0] px-2 py-0.5 text-xs text-[#2f9e6e]'
                      }
                    >
                      {student.offCampusOnly ? '纯课外' : '托管'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {student.offCampusOnly && (
                      <button
                        type="button"
                        onClick={() => setStatementStudent({ id: student.id, name: student.name })}
                        className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                      >
                        对账单/充值
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {students.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无学生
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      {statementStudent && (
        <AdminCourseStatementModal
          studentId={statementStudent.id}
          studentName={statementStudent.name}
          onClose={() => setStatementStudent(null)}
        />
      )}
    </div>
  )
}
