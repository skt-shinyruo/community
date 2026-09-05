// 加载态守卫：首载骨架统一走 UiSkeleton，UiState 只承担 empty / error / development 结果状态。
// 裸「加载中」文本已随波次 10 清零并收紧为零允许：UiSkeleton 的 sr-only 可访问名称是唯一受认可的
// 来源，任何新增出现（新文件或次数变化）直接失败。规范见 docs/handbook/frontend-ui-optimization.md 6.3。
import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'

// UiSkeleton 的 sr-only 可访问名称是唯一受认可的「加载中」来源。
const SANCTIONED = new Map([
  ['src/components/ui/UiSkeleton.vue', 1]
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
  it('keeps 加载中 occurrences on the zero-tolerance sanctioned registry', () => {
    const found = new Map()
    for (const path of collectSources()) {
      const count = (readFileSync(resolve(process.cwd(), path), 'utf8').match(/加载中/g) || []).length
      if (count > 0) found.set(path, count)
    }
    expect([...found.entries()].sort()).toEqual([...SANCTIONED.entries()].sort())
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
