import { BrandMark } from '../brand/BrandMark'

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
      <div className="no-class-screen">
        <p>您还未被分配托管班或课外课，请联系管理员</p>
        <button type="button" onClick={onOpenRoster} className="logout-btn">
          前往学生管理创建托管班
        </button>
      </div>
    </div>
  )
}
