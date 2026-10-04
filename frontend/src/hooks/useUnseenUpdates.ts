import { useState } from 'react'
import { PRODUCT_UPDATES } from '../updates'

const STORAGE_KEY = 'lastSeenUpdateId'
const latestId = PRODUCT_UPDATES[0]?.id ?? ''

function readLastSeenId(): string {
  try {
    return window.localStorage.getItem(STORAGE_KEY) ?? ''
  } catch {
    return ''
  }
}

export function useUnseenUpdates() {
  const [lastSeenId, setLastSeenId] = useState(readLastSeenId)
  const hasUnseen = latestId !== '' && lastSeenId < latestId

  function markSeen() {
    setLastSeenId(latestId)
    try {
      window.localStorage.setItem(STORAGE_KEY, latestId)
    } catch {
      // 隐私模式/禁用存储时静默忽略，不影响当次查看
    }
  }

  return { hasUnseen, markSeen }
}
