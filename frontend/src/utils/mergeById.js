import { normalizeOpaqueId } from './opaqueId'

/**
 * @template T
 * @param {(item: T) => unknown} [getId] 缺省读取 `item.id`；列表按其他字段（如 postId）归并时显式传入。
 * @returns {(item: T) => string}
 */
function idKeyOf(getId) {
  const read = typeof getId === 'function'
    ? getId
    : (item) => /** @type {Record<string, unknown> | null | undefined} */ (item)?.id
  return (item) => normalizeOpaqueId(read(item))
}

/**
 * 追加分页归并：existing 原样保留，fresh 中 id 已出现（含 fresh 内部重复）或缺 id 的项被丢弃，
 * 消除页间位移造成的重复卡片。
 *
 * @template T
 * @param {T[]} existing
 * @param {T[]} fresh
 * @param {(item: T) => unknown} [getId]
 * @returns {T[]}
 */
export function mergeAppendedById(existing, fresh, getId) {
  const keyOf = idKeyOf(getId)
  const base = Array.isArray(existing) ? existing : []
  const seen = new Set(base.map((item) => keyOf(item)))
  const additions = []
  for (const item of Array.isArray(fresh) ? fresh : []) {
    const id = keyOf(item)
    if (!id || seen.has(id)) continue
    seen.add(id)
    additions.push(item)
  }
  return [...base, ...additions]
}

/**
 * 头部归并（重读第一页后静默更新）：fresh 放头部，existing 中与 fresh 同 id 的项移除，
 * 保留 fresh 的最新版本。
 *
 * @template T
 * @param {T[]} existing
 * @param {T[]} fresh
 * @param {(item: T) => unknown} [getId]
 * @returns {T[]}
 */
export function mergePrependedById(existing, fresh, getId) {
  const keyOf = idKeyOf(getId)
  const head = Array.isArray(fresh) ? fresh : []
  const freshIds = new Set(head.map((item) => keyOf(item)))
  const rest = (Array.isArray(existing) ? existing : []).filter((item) => !freshIds.has(keyOf(item)))
  return [...head, ...rest]
}
