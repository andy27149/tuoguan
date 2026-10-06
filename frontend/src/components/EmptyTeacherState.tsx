import { BrandMark } from '../brand/BrandMark'
import { EmptyState } from './EmptyState'

interface EmptyTeacherStateProps {
  onOpenRoster: () => void
  onLogout: () => void
}

export function EmptyTeacherState({ onOpenRoster, onLogout }: EmptyTeacherStateProps) {
  return (
    <div className="min-h-screen pb-8">
      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">
            <BrandMark size={22} />
            托管班看板
          </h1>
          <button type="button" onClick={onLogout} className="logout-btn">
            退出登录
          </button>
        </div>
      </header>
      <EmptyState icon="👋" message="欢迎加入！管理员还没有把你安排进任何托管班或课外课，安排好之后这里会自动显示。">
        <button type="button" onClick={onOpenRoster} className="logout-btn">
          前往学生管理
        </button>
      </EmptyState>
    </div>
  )
}
