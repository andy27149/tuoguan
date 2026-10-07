const BEIJING_TIME_ZONE = 'Asia/Shanghai'

/**
 * 后端以 UTC Instant（带 Z 后缀）返回时间戳。直接用 Date#getFullYear 等本地方法读取会
 * 跟着设备/浏览器自身时区走，设备时区不是北京时间时显示就会错（包括导出的账单图片）。
 * 这里显式指定 Asia/Shanghai，保证无论运行环境时区如何都固定显示北京时间。
 */
function shanghaiDateTimeParts(isoString: string) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: BEIJING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(isoString))
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? ''
  return { year: get('year'), month: get('month'), day: get('day'), hour: get('hour'), minute: get('minute') }
}

/** 统一展示用的日期格式：YYYY-MM-DD（北京时间），取代各处不一致的 toLocaleDateString() 写法。 */
export function formatDate(isoString: string): string {
  const { year, month, day } = shanghaiDateTimeParts(isoString)
  return `${year}-${month}-${day}`
}

/** 统一展示用的日期时间格式：YYYY-MM-DD HH:mm（北京时间），取代各处不一致的 toLocaleString() 写法。 */
export function formatDateTime(isoString: string): string {
  const { year, month, day, hour, minute } = shanghaiDateTimeParts(isoString)
  return `${year}-${month}-${day} ${hour}:${minute}`
}

export function todayDateString(): string {
  const now = new Date()
  const yyyy = now.getFullYear()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

export function currentTimeString(): string {
  const now = new Date()
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
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

/** Returns a flat list of week cells (padded with null) for a calendar grid of the given month. */
export function monthCalendarCells(month: string): (string | null)[] {
  const [yearStr, monthStr] = month.split('-')
  const year = Number(yearStr)
  const monthIndex = Number(monthStr) - 1
  const startWeekday = new Date(year, monthIndex, 1).getDay()
  const daysInMonth = new Date(year, monthIndex + 1, 0).getDate()

  const cells: (string | null)[] = []
  for (let i = 0; i < startWeekday; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) {
    cells.push(`${yearStr}-${monthStr}-${String(d).padStart(2, '0')}`)
  }
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
}
