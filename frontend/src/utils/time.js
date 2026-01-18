// 时间工具：将后端返回的 Date/Instant 字段格式化为可读字符串。

export function formatTime(value) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return String(value)
  }
}

