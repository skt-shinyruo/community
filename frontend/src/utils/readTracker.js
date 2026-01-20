// 本地“已读/未读”追踪（轻量版）。
// 目标：实现类似 Discourse 的 topic 未读提示，但不依赖后端“已读时间线”。

const STORAGE_KEY = 'community.read.posts.v1'
const MAX_ITEMS = 800

function safeParse(json) {
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
}

function nowMs() {
  return Date.now()
}

function loadState() {
  if (typeof window === 'undefined') return { lastSeenAt: 0, items: {} }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY) || ''
    const parsed = raw ? safeParse(raw) : null
    const lastSeenAt = Number(parsed?.lastSeenAt || 0)
    const items = parsed?.items && typeof parsed.items === 'object' ? parsed.items : {}
    return { lastSeenAt: Number.isFinite(lastSeenAt) ? lastSeenAt : 0, items }
  } catch {
    return { lastSeenAt: 0, items: {} }
  }
}

function saveState(state) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state || {}))
  } catch {
    // ignore
  }
}

function normalizePostId(postId) {
  const id = Number(postId || 0)
  return Number.isFinite(id) ? id : 0
}

function pruneItems(items) {
  const entries = Object.entries(items || {})
    .map(([k, v]) => [String(k), Number(v || 0)])
    .filter(([, v]) => Number.isFinite(v) && v > 0)
    .sort((a, b) => b[1] - a[1])

  const pruned = {}
  for (const [k, v] of entries.slice(0, MAX_ITEMS)) {
    pruned[k] = v
  }
  return pruned
}

// 仅获取当前 baseline（不更新 lastSeenAt）。
// - 首次访问返回 now（避免全量显示“未读”）。
export function getPostsListBaselineAt() {
  const state = loadState()
  const prev = Number(state.lastSeenAt || 0)
  const now = nowMs()
  return prev > 0 ? prev : now
}

// 返回“上一轮 lastSeenAt”（用于本次页面渲染判断），并把 lastSeenAt 更新为当前时间用于下一次。
export function touchPostsListSeen() {
  const state = loadState()
  const prev = Number(state.lastSeenAt || 0)
  const now = nowMs()

  // 首次访问：不希望全量显示“未读”，因此 baseline 直接设为 now。
  const baseline = prev > 0 ? prev : now

  saveState({
    lastSeenAt: now,
    items: pruneItems(state.items)
  })

  return baseline
}

export function markPostRead(postId, at = nowMs()) {
  const id = normalizePostId(postId)
  if (!id) return

  const state = loadState()
  const nextAt = Number(at || 0)
  if (!Number.isFinite(nextAt) || nextAt <= 0) return

  const items = { ...(state.items || {}) }
  items[String(id)] = nextAt

  saveState({
    lastSeenAt: Number(state.lastSeenAt || 0),
    items: pruneItems(items)
  })
}

export function getPostReadAt(postId) {
  const id = normalizePostId(postId)
  if (!id) return 0
  const state = loadState()
  const v = Number(state?.items?.[String(id)] || 0)
  return Number.isFinite(v) ? v : 0
}
