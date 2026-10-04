import { useState } from 'react'
import { createPortal } from 'react-dom'
import { PRODUCT_UPDATES } from '../updates'
import { useUnseenUpdates } from '../hooks/useUnseenUpdates'

export function UpdatesButton() {
  const { hasUnseen, markSeen } = useUnseenUpdates()
  const [open, setOpen] = useState(false)

  function handleOpen() {
    setOpen(true)
    markSeen()
  }

  return (
    <>
      <button type="button" onClick={handleOpen} className="updates-btn" aria-label="最近更新">
        <span aria-hidden="true">🔔</span>
        {hasUnseen && <span className="updates-btn__dot" aria-hidden="true" />}
      </button>

      {open &&
        createPortal(
          <div
            className="share-overlay is-visible"
            onClick={(e) => {
              if (e.target === e.currentTarget) setOpen(false)
            }}
          >
            <div className="share-modal" role="dialog" aria-modal="true" aria-label="最近更新">
              <button type="button" className="share-modal__close" aria-label="关闭" onClick={() => setOpen(false)}>
                ×
              </button>
              <div className="stats-modal__header">
                <span className="stats-modal__title">最近更新</span>
              </div>
              <ul className="updates-list">
                {PRODUCT_UPDATES.map((update) => (
                  <li key={update.id} className="updates-list__entry">
                    <p className="updates-list__date">{update.date}</p>
                    <ul className="updates-list__items">
                      {update.items.map((item, i) => (
                        <li key={i}>{item}</li>
                      ))}
                    </ul>
                  </li>
                ))}
              </ul>
            </div>
          </div>,
          document.body,
        )}
    </>
  )
}
