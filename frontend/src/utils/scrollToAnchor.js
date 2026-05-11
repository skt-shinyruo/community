// 滚动定位工具：用于评论/回复锚点定位（hash 或 query），并提供短暂高亮提示。

export function scrollToAnchor(anchorId, options = {}) {
  if (typeof document === 'undefined') return false

  const id = String(anchorId || '').trim()
  if (!id) return false

  const {
    behavior = 'smooth',
    block = 'center',
    highlightClass = 'anchor-highlight',
    highlightMs = 1600
  } = options || {}

  const el = document.getElementById(id)
  if (!el) return false

  try {
    el.scrollIntoView({ behavior, block })
  } catch {
    // 滚动 API 抛错时回退为坐标滚动。
    try {
      const top = el.getBoundingClientRect().top + window.scrollY - 80
      window.scrollTo({ top: Math.max(0, top), behavior })
    } catch {
      return false
    }
  }

  if (highlightClass) {
    el.classList.add(highlightClass)
    window.setTimeout(() => el.classList.remove(highlightClass), Number(highlightMs || 0))
  }

  return true
}
