import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon?: string
  message: string
  children?: ReactNode
}

export function EmptyState({ icon = '📋', message, children }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-state__icon" aria-hidden="true">
        {icon}
      </span>
      <p className="empty-state__message">{message}</p>
      {children && <div className="empty-state__actions">{children}</div>}
    </div>
  )
}
