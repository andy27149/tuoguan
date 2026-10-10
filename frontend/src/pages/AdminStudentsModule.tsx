import { useEffect, useMemo, useState } from 'react'
import * as courseApi from '../api/course'
import * as unitApi from '../api/unit'
import { ApiError } from '../api/client'
import { AdminCourseStatementModal } from '../components/AdminCourseStatementModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { CreateStudentModal } from '../components/CreateStudentModal'
import { Pagination } from '../components/Pagination'

export function AdminStudentsModule() {
  const [students, setStudents] = useState<courseApi.AdminStudent[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [classRooms, setClassRooms] = useState<unitApi.TeachingUnit[]>([])
  const [offCampusCourses, setOffCampusCourses] = useState<unitApi.TeachingUnit[]>([])

  const [showCreateModal, setShowCreateModal] = useState(false)

  type IdentityFilter = 'ALL' | 'CUSTODY' | 'OFF_CAMPUS_ONLY'
  const [identityFilter, setIdentityFilter] = useState<IdentityFilter>('ALL')
  const [teacherFilter, setTeacherFilter] = useState('')
  const [nameQuery, setNameQuery] = useState('')

  const PAGE_SIZE = 20
  const [currentPage, setCurrentPage] = useState(1)

  const [statementStudent, setStatementStudent] = useState<{ id: number; name: string } | null>(null)

  const [editingStudent, setEditingStudent] = useState<courseApi.AdminStudent | null>(null)
  const [editingName, setEditingName] = useState('')
  const [editingSchoolClassName, setEditingSchoolClassName] = useState('')
  const [editingTeachingUnitId, setEditingTeachingUnitId] = useState('')
  const [editingCourseIds, setEditingCourseIds] = useState<number[]>([])
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)

  const [confirmingDeactivate, setConfirmingDeactivate] = useState<courseApi.AdminStudent | null>(null)
  const [statusSubmitting, setStatusSubmitting] = useState(false)
  const [statusError, setStatusError] = useState<string | null>(null)

  function loadStudents() {
    setLoading(true)
    setLoadError(null)
    courseApi
      .fetchAdminStudents()
      .then(setStudents)
      .catch(() => setLoadError('加载学生列表失败，请刷新重试'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadStudents()
    unitApi
      .fetchTeachingUnits('MONTHLY')
      .then(setClassRooms)
      .catch(() => setClassRooms([]))
    unitApi
      .fetchTeachingUnits('LESSON_COUNT')
      .then((all) => setOffCampusCourses(all.filter((c) => c.active)))
      .catch(() => setOffCampusCourses([]))
  }, [])

  const teacherOptions = useMemo(() => {
    const names = new Set<string>()
    students.forEach((s) => {
      if (s.teacherName) names.add(s.teacherName)
    })
    return Array.from(names).sort((a, b) => a.localeCompare(b))
  }, [students])

  const filteredStudents = useMemo(() => {
    const query = nameQuery.trim().toLowerCase()
    return students.filter((s) => {
      if (identityFilter === 'CUSTODY' && s.offCampusOnly) return false
      if (identityFilter === 'OFF_CAMPUS_ONLY' && !s.offCampusOnly) return false
      if (teacherFilter && s.teacherName !== teacherFilter) return false
      if (query && !s.name.toLowerCase().includes(query)) return false
      return true
    })
  }, [students, identityFilter, teacherFilter, nameQuery])

  const totalPages = Math.max(1, Math.ceil(filteredStudents.length / PAGE_SIZE))
  const currentPageClamped = Math.min(currentPage, totalPages)
  const pageItems = useMemo(
    () => filteredStudents.slice((currentPageClamped - 1) * PAGE_SIZE, currentPageClamped * PAGE_SIZE),
    [filteredStudents, currentPageClamped],
  )

  function handleIdentityFilterChange(value: IdentityFilter) {
    setIdentityFilter(value)
    setCurrentPage(1)
  }

  function handleTeacherFilterChange(value: string) {
    setTeacherFilter(value)
    setCurrentPage(1)
  }

  function handleNameQueryChange(value: string) {
    setNameQuery(value)
    setCurrentPage(1)
  }

  function toggleCourseId(courseIds: number[], courseId: number): number[] {
    return courseIds.includes(courseId) ? courseIds.filter((id) => id !== courseId) : [...courseIds, courseId]
  }

  async function handleCreateStudent(
    name: string,
    schoolClassName: string | null,
    teachingUnitId: number | null,
    courseIds: number[],
  ) {
    await courseApi.createAdminStudent(name, schoolClassName, teachingUnitId, courseIds)
    loadStudents()
  }

  function handleStartEdit(student: courseApi.AdminStudent) {
    setEditingStudent(student)
    setEditingName(student.name)
    setEditingSchoolClassName(student.schoolClassName ?? '')
    setEditingTeachingUnitId(student.classRoomId ? String(student.classRoomId) : '')
    setEditingCourseIds(student.enrolledCourseIds)
    setEditError(null)
  }

  function handleCancelEdit() {
    setEditingStudent(null)
    setEditError(null)
  }

  async function handleSaveEdit() {
    if (!editingStudent) return
    const name = editingName.trim()
    if (!name) return
    setEditSubmitting(true)
    setEditError(null)
    try {
      await courseApi.updateAdminStudent(
        editingStudent.id,
        name,
        editingSchoolClassName.trim() || null,
        editingTeachingUnitId ? Number(editingTeachingUnitId) : null,
        editingStudent.enrolled,
        editingCourseIds,
      )
      setEditingStudent(null)
      loadStudents()
    } catch (err) {
      if (err instanceof ApiError && (err.status === 404 || err.status === 400)) {
        setEditError('所选托管班不可用，请刷新后重试')
      } else {
        setEditError('保存失败，请重试')
      }
    } finally {
      setEditSubmitting(false)
    }
  }

  async function handleReactivate(student: courseApi.AdminStudent) {
    setStatusSubmitting(true)
    setStatusError(null)
    try {
      await courseApi.updateAdminStudent(
        student.id,
        student.name,
        student.schoolClassName,
        student.classRoomId,
        true,
        student.enrolledCourseIds,
      )
      loadStudents()
    } catch {
      setStatusError('启用失败，请重试')
    } finally {
      setStatusSubmitting(false)
    }
  }

  async function handleConfirmDeactivate() {
    if (!confirmingDeactivate) return
    setStatusSubmitting(true)
    setStatusError(null)
    try {
      await courseApi.updateAdminStudent(
        confirmingDeactivate.id,
        confirmingDeactivate.name,
        confirmingDeactivate.schoolClassName,
        confirmingDeactivate.classRoomId,
        false,
        confirmingDeactivate.enrolledCourseIds,
      )
      setConfirmingDeactivate(null)
      loadStudents()
    } catch {
      setStatusError('停用失败，请重试')
    } finally {
      setStatusSubmitting(false)
    }
  }

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">学生管理</h2>
        <button
          type="button"
          onClick={() => setShowCreateModal(true)}
          className="rounded-full bg-[#6d5bd0] px-4 py-1.5 text-sm font-medium text-white"
        >
          + 新建学生
        </button>
      </div>

      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <div className="flex flex-wrap items-end gap-3">
          <div className="text-xs font-semibold text-[#5d5480]">
            身份
            <select
              aria-label="身份筛选"
              value={identityFilter}
              onChange={(e) => handleIdentityFilterChange(e.target.value as IdentityFilter)}
              className="mt-1 block rounded-lg border border-[#ece7de] px-2.5 py-1.5 text-sm text-[#241f3d]"
            >
              <option value="ALL">全部</option>
              <option value="CUSTODY">托管</option>
              <option value="OFF_CAMPUS_ONLY">纯课外</option>
            </select>
          </div>
          <div className="text-xs font-semibold text-[#5d5480]">
            托管教师
            <select
              aria-label="托管教师筛选"
              value={teacherFilter}
              onChange={(e) => handleTeacherFilterChange(e.target.value)}
              className="mt-1 block rounded-lg border border-[#ece7de] px-2.5 py-1.5 text-sm text-[#241f3d]"
            >
              <option value="">全部</option>
              {teacherOptions.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div className="text-xs font-semibold text-[#5d5480]">
            姓名
            <input
              aria-label="姓名搜索"
              placeholder="搜索姓名"
              value={nameQuery}
              onChange={(e) => handleNameQueryChange(e.target.value)}
              className="mt-1 block rounded-lg border border-[#ece7de] px-2.5 py-1.5 text-sm text-[#241f3d]"
            />
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">学生总览（{filteredStudents.length}）</h2>
        {loadError && <p className="mt-2 text-sm text-[#b7591f]">{loadError}</p>}
        {editError && <p className="mt-2 text-sm text-[#b7591f]">{editError}</p>}
        {statusError && <p className="mt-2 text-sm text-[#b7591f]">{statusError}</p>}
        {loading && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}
        {!loading && (
          <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="text-xs whitespace-nowrap text-[#7c7391]">
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">姓名</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">学籍班</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管班</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">托管教师</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">身份</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">状态</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">课外课报名</th>
                <th className="border-b border-[#ece7de] bg-[#faf7ff] px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((student) => {
                const isEditing = editingStudent?.id === student.id
                return (
                  <tr key={student.id} className="border-b border-[#ece7de] align-middle hover:bg-[#faf7ff]">
                    <td className="px-4 py-3 whitespace-nowrap">
                      {isEditing ? (
                        <input
                          aria-label={`学生姓名${student.id}`}
                          value={editingName}
                          onChange={(e) => setEditingName(e.target.value)}
                          className="w-24 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="font-medium text-[#241f3d]">{student.name}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {isEditing ? (
                        <input
                          aria-label={`学籍班${student.id}`}
                          value={editingSchoolClassName}
                          onChange={(e) => setEditingSchoolClassName(e.target.value)}
                          className="w-24 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        />
                      ) : (
                        <span className="text-[#7c7391]">{student.schoolClassName || '—'}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {isEditing ? (
                        <select
                          aria-label={`托管班${student.id}`}
                          value={editingTeachingUnitId}
                          onChange={(e) => setEditingTeachingUnitId(e.target.value)}
                          className="rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                        >
                          <option value="">纯课外课学生</option>
                          {classRooms.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.name}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span className="text-[#7c7391]">{student.classRoomName || '—'}</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="text-[#7c7391]">{student.teacherName || '—'}</span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
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
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={
                          student.enrolled
                            ? 'rounded-full bg-[#e9f7f0] px-2 py-0.5 text-xs text-[#2f9e6e]'
                            : 'rounded-full bg-[#f1f0f4] px-2 py-0.5 text-xs text-[#7c7391]'
                        }
                      >
                        {student.enrolled ? '已启用' : '已停用'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <div className="flex flex-wrap gap-2">
                          {offCampusCourses.map((c) => (
                            <label key={c.id} className="flex items-center gap-1 text-xs text-[#5d5480]">
                              <input
                                type="checkbox"
                                aria-label={`${c.name}${student.id}`}
                                checked={editingCourseIds.includes(c.id)}
                                onChange={() => setEditingCourseIds((prev) => toggleCourseId(prev, c.id))}
                              />
                              {c.name}
                            </label>
                          ))}
                        </div>
                      ) : student.enrolledCourseNames.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {student.enrolledCourseNames.map((name) => (
                            <span
                              key={name}
                              className="rounded-full bg-[#eef0fd] px-2 py-0.5 text-xs text-[#4b4a9c]"
                            >
                              {name}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-[#7c7391]">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {isEditing ? (
                        <span className="flex flex-wrap gap-1.5">
                          <button
                            type="button"
                            onClick={handleSaveEdit}
                            disabled={editSubmitting || !editingName.trim()}
                            className="shrink-0 rounded-full bg-[#6d5bd0] px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
                          >
                            保存
                          </button>
                          <button
                            type="button"
                            onClick={handleCancelEdit}
                            disabled={editSubmitting}
                            className="shrink-0 rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            取消
                          </button>
                        </span>
                      ) : (
                        <span className="flex flex-wrap gap-1.5">
                          <button
                            type="button"
                            aria-label={`编辑${student.name}`}
                            onClick={() => handleStartEdit(student)}
                            className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                          >
                            编辑
                          </button>
                          {student.enrolled ? (
                            <button
                              type="button"
                              aria-label={`停用${student.name}`}
                              onClick={() => setConfirmingDeactivate(student)}
                              disabled={statusSubmitting}
                              className="rounded-full border border-[#f3d0c0] px-3 py-1 text-xs text-[#b7591f] hover:bg-[#fdf1e6] disabled:opacity-50"
                            >
                              停用
                            </button>
                          ) : (
                            <button
                              type="button"
                              aria-label={`启用${student.name}`}
                              onClick={() => handleReactivate(student)}
                              disabled={statusSubmitting}
                              className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff] disabled:opacity-50"
                            >
                              启用
                            </button>
                          )}
                          {(student.offCampusOnly || student.enrolledCourseNames.length > 0) && (
                            <button
                              type="button"
                              onClick={() => setStatementStudent({ id: student.id, name: student.name })}
                              className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                            >
                              对账单/充值
                            </button>
                          )}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
              {filteredStudents.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-3 text-xs text-[#7c7391]">
                    暂无学生
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <Pagination
            page={currentPageClamped}
            totalPages={totalPages}
            totalItems={filteredStudents.length}
            onPageChange={setCurrentPage}
          />
          </div>
        )}
      </div>

      {showCreateModal && (
        <CreateStudentModal
          classRooms={classRooms}
          offCampusCourses={offCampusCourses}
          onCreate={handleCreateStudent}
          onClose={() => setShowCreateModal(false)}
        />
      )}

      {statementStudent && (
        <AdminCourseStatementModal
          studentId={statementStudent.id}
          studentName={statementStudent.name}
          onClose={() => setStatementStudent(null)}
        />
      )}

      {confirmingDeactivate && (
        <ConfirmDialog
          title="停用学生"
          message={`停用${confirmingDeactivate.name}后，该学生将从看板上隐藏，已产生的记录不会被删除，可随时重新启用。确认停用吗？`}
          confirmLabel="确认停用"
          confirming={statusSubmitting}
          onConfirm={handleConfirmDeactivate}
          onCancel={() => setConfirmingDeactivate(null)}
        />
      )}
    </div>
  )
}
