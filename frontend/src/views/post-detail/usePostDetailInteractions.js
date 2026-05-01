export function isWithinEditWindow(createTime, windowMs) {
  const t = new Date(createTime).getTime()
  if (!Number.isFinite(t) || t <= 0) return false
  return Date.now() - t <= windowMs
}
