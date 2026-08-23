export function todayDateString(): string {
  const now = new Date()
  const yyyy = now.getFullYear()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

export function currentMonthString(): string {
  const now = new Date()
  const yyyy = now.getFullYear()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  return `${yyyy}-${mm}`
}

export function shiftMonthString(month: string, delta: number): string {
  const [yearStr, monthStr] = month.split('-')
  const year = Number(yearStr)
  const monthIndex = Number(monthStr) - 1
  const shifted = new Date(year, monthIndex + delta, 1)
  const yyyy = shifted.getFullYear()
  const mm = String(shifted.getMonth() + 1).padStart(2, '0')
  return `${yyyy}-${mm}`
}
