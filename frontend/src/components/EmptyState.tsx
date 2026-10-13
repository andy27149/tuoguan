import type { ReactNode } from 'react'

interface EmptyStateProps {
  /** 默认是个 emoji 字符串；也可以传任意节点（比如吉祥物插画）替代。 */
  icon?: ReactNode
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
