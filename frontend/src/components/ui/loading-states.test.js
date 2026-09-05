// 加载态守卫：首载骨架统一走 UiSkeleton，UiState 只承担 empty / error / development 结果状态。
// 裸「加载中」文本按下方登记表冻结（文件 → 出现次数），随页面迁移波次递减直至清零；
// 新增出现（新文件或登记文件次数变多）直接失败。规范见 docs/handbook/frontend-ui-optimization.md 6.3。
import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'

// UiSkeleton 的 sr-only 可访问名称是唯一受认可的「加载中」来源。
const SANCTIONED = new Map([
  ['src/components/ui/UiSkeleton.vue', 1]
])

// 迁移债：各页面簇在后续波次迁往 UiSkeleton / 尾部指示 / 按钮 loading 后删除对应行。
const LEGACY_LOADING_TEXT = new Map([
  ['src/views/AnalyticsView.vue', 1],
  ['src/views/MarketInventoryView.vue', 1],
  ['src/views/MarketMyListingsView.vue', 1],
  ['src/views/MarketOrderListView.vue', 1],
  ['src/views/ModerationView.vue', 3]
])

function collectSources() {
  const root = resolve(process.cwd(), 'src')
  const files = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = resolve(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full)
      } else if ((entry.name.endsWith('.vue') || entry.name.endsWith('.js')) && !entry.name.endsWith('.test.js')) {
        files.push(`src/${full.slice(root.length + 1)}`)
      }
    }
  }
  walk(root)
  return files
}

describe('loading state guardrails', () => {
  it('keeps 加载中 occurrences on the sanctioned / legacy registry, with no new additions', () => {
    const found = new Map()
    for (const path of collectSources()) {
      const count = (readFileSync(resolve(process.cwd(), path), 'utf8').match(/加载中/g) || []).length
      if (count > 0) found.set(path, count)
    }
    const registered = new Map([...SANCTIONED, ...LEGACY_LOADING_TEXT])
    expect([...found.entries()].sort()).toEqual([...registered.entries()].sort())
  })

  it('keeps UiState limited to empty / error / development result states', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/components/ui/UiState.vue'), 'utf8')
    const variants = source.match(/variants\s*=\s*\[([^\]]*)\]/)?.[1] || ''
    expect(variants).toContain("'empty'")
    expect(variants).toContain("'error'")
    expect(variants).toContain("'development'")
    expect(variants).not.toMatch(/loading|pending|forbidden|unavailable/)
  })

  it('routes the accessible loading label through UiSkeleton only', () => {
    const skeleton = readFileSync(resolve(process.cwd(), 'src/components/ui/UiSkeleton.vue'), 'utf8')
    expect(skeleton).toContain('role="status"')
    expect(skeleton).toContain('sr-only')
  })
})
