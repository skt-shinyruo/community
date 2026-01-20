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
export function formatTimeAgo(value) {
  if (!value) return '-'
  const t = new Date(value).getTime()
  if (!Number.isFinite(t)) return formatTime(value)

  const diff = Date.now() - t
  if (!Number.isFinite(diff)) return formatTime(value)

  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`
  if (diff < 7 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / (24 * 60 * 60 * 1000))} 天前`
  return new Date(t).toLocaleDateString()
}
