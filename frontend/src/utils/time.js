// 时间工具：将后端返回的 Date/Instant 字段格式化为可读字符串。

export function formatTime(value) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return String(value)
  }
}

// 相对时间：用于列表页“活动/最后回复”快速扫读。
const relativeTime = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'auto' })
const shortDate = new Intl.DateTimeFormat()

export function formatTimeAgo(value) {
  if (!value) return '-'
  const t = new Date(value).getTime()
  if (!Number.isFinite(t)) return formatTime(value)

  const seconds = Math.floor((Date.now() - t) / 1000)
  if (seconds < 60) return '刚刚'
  if (seconds < 60 * 60) return relativeTime.format(-Math.floor(seconds / 60), 'minute')
  if (seconds < 24 * 60 * 60) return relativeTime.format(-Math.floor(seconds / (60 * 60)), 'hour')
  if (seconds < 7 * 24 * 60 * 60) return relativeTime.format(-Math.floor(seconds / (24 * 60 * 60)), 'day')
  return shortDate.format(new Date(t))
}

// 会话列表时间：当天显示时分，跨天显示日期。
export function formatConversationTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const isToday =
    d.getDate() === now.getDate() && d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()

  if (isToday) {
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString()
}

// 本地日历日（YYYY-MM-DD）：数据看板默认「今天」等场景按用户本地时区取日；
// 不能用 toISOString().slice(0, 10)（UTC 口径，UTC+8 用户每天 0-8 点会看到昨天）。
export function formatLocalDate(value = new Date()) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}
