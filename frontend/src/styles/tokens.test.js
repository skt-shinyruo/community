// 设计令牌与全局样式守卫：variables.css 是唯一令牌来源，本文件锁定规范批准的
// Indigo 令牌、暗色表面、链接色、圆角、z-index 七阶与动效令牌，并对全部样式源做
// 静态检查（未定义令牌、hex fallback、页面级 data-theme 覆盖、z-index 直写、
// reduced-motion 守卫、WCAG 对比度、原语内部类视图基线与表单原语 focus ring）。
// 规范见 docs/handbook/frontend-ui-optimization.md。

import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'

function read(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

function cssBlock(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`, 's'))
  return match?.[1] || ''
}

function declarations(block) {
  const map = new Map()
  for (const match of block.matchAll(/--([a-z0-9-]+)\s*:\s*([^;]+);/gi)) {
    map.set(match[1].toLowerCase(), match[2].trim().toLowerCase())
  }
  return map
}

function collectStyleSources() {
  const root = resolve(process.cwd(), 'src')
  const files = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = resolve(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full)
      } else if (entry.name.endsWith('.css') || entry.name.endsWith('.vue')) {
        files.push(full)
      }
    }
  }
  walk(root)
  return files.map((full) => {
    const raw = readFileSync(full, 'utf8')
    const rel = `src/${full.slice(root.length + 1)}`
    if (full.endsWith('.css')) return { path: rel, css: raw }
    const blocks = [...raw.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/gi)].map((m) => m[1])
    return { path: rel, css: blocks.join('\n') }
  })
}

function parseHexColor(hex) {
  const value = hex.replace('#', '')
  const full = value.length === 3 ? value.split('').map((c) => c + c).join('') : value
  return {
    r: parseInt(full.slice(0, 2), 16),
    g: parseInt(full.slice(2, 4), 16),
    b: parseInt(full.slice(4, 6), 16)
  }
}

function channelLuminance(v) {
  const c = v / 255
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

function luminance(hex) {
  const { r, g, b } = parseHexColor(hex)
  return 0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b)
}

function contrastRatio(fg, bg) {
  const lf = luminance(fg)
  const lb = luminance(bg)
  return (Math.max(lf, lb) + 0.05) / (Math.min(lf, lb) + 0.05)
}

const variables = read('src/styles/variables.css')
const light = declarations(cssBlock(variables, ':root'))
const dark = declarations(cssBlock(variables, "html[data-theme='dark']"))

function expectTokens(map, expected, themeLabel) {
  for (const [name, value] of Object.entries(expected)) {
    expect(map.get(name), `${themeLabel} --${name}`).toBe(value)
  }
}

describe('design tokens', () => {
  it('defines the approved Indigo / link / text tokens in the light theme', () => {
    expectTokens(light, {
      accent: '#3e63dd',
      'accent-hover': '#3358d4',
      'accent-weak': '#edf2fe',
      'accent-text': '#3a5bc7',
      'accent-contrast': '#ffffff',
      'link-color': '#2563eb',
      'text-3': '#666666',
      muted: '#6b6b6b'
    }, 'light')
  })

  it('defines the approved Indigo / link / surface tokens in the dark theme', () => {
    expectTokens(dark, {
      bg: '#0d0e12',
      surface: '#131418',
      'surface-2': '#1a1c22',
      'surface-3': '#23262e',
      border: '#2a2d36',
      'border-strong': '#5d6373',
      accent: '#3e63dd',
      'accent-hover': '#5472e4',
      'accent-weak': '#182449',
      'accent-text': '#9eb1ff',
      'accent-contrast': '#ffffff',
      'link-color': '#4a86ff',
      'text-3': '#8f8f8f',
      muted: '#8a8a8a'
    }, 'dark')
  })

  it('keeps radius, z-index and motion on the approved semantic scales', () => {
    expectTokens(light, {
      'radius-sm': '6px',
      'radius-md': '8px',
      'radius-lg': '12px',
      'z-raised': '1',
      'z-sticky': '40',
      'z-nav': '60',
      'z-overlay': '100',
      'z-popover': '200',
      'z-modal': '300',
      'z-toast': '1000',
      'duration-instant': '70ms',
      'duration-fast': '110ms',
      'duration-base': '150ms',
      'duration-slow': '240ms',
      'duration-slower': '400ms'
    }, 'light')
    expect(light.get('ease-standard')).toContain('cubic-bezier')
    expect(light.get('ease-enter')).toContain('cubic-bezier')
    expect(light.get('ease-exit')).toContain('cubic-bezier')
  })

  it('keeps compact density as a token-only override over the comfortable defaults', () => {
    const compact = declarations(cssBlock(variables, "html[data-density='compact']"))
    expect(compact.size).toBeGreaterThan(0)
    expect(compact.get('control-height')).toBe('32px')
  })

  it('meets WCAG contrast thresholds in both themes', () => {
    const themes = [
      ['light', light],
      ['dark', dark]
    ]
    const surfaces = ['bg', 'surface', 'surface-2', 'surface-3']
    for (const [label, tokens] of themes) {
      for (const surface of surfaces) {
        for (const text of ['text-1', 'text-2', 'text-3']) {
          const ratio = contrastRatio(tokens.get(text), tokens.get(surface))
          expect(ratio, `${label} ${text} on ${surface}`).toBeGreaterThanOrEqual(4.5)
        }
      }
      // --muted 只用于装饰、禁用和占位，这些位置落在 bg / surface / surface-2 上。
      for (const surface of ['bg', 'surface', 'surface-2']) {
        const ratio = contrastRatio(tokens.get('muted'), tokens.get(surface))
        expect(ratio, `${label} muted on ${surface}`).toBeGreaterThanOrEqual(4.5)
      }
      for (const surface of ['bg', 'surface']) {
        const linkRatio = contrastRatio(tokens.get('link-color'), tokens.get(surface))
        expect(linkRatio, `${label} link-color on ${surface}`).toBeGreaterThanOrEqual(4.5)
        const borderRatio = contrastRatio(tokens.get('border-strong'), tokens.get(surface))
        expect(borderRatio, `${label} border-strong on ${surface}`).toBeGreaterThanOrEqual(3)
      }
      const accentRatio = contrastRatio(tokens.get('accent-contrast'), tokens.get('accent'))
      expect(accentRatio, `${label} accent-contrast on accent`).toBeGreaterThanOrEqual(4.5)
      const accentTextRatio = contrastRatio(tokens.get('accent-text'), tokens.get('accent-weak'))
      expect(accentTextRatio, `${label} accent-text on accent-weak`).toBeGreaterThanOrEqual(4.5)
    }
  })
})

describe('global style guardrails', () => {
  const sources = collectStyleSources()

  it('resolves every var() reference to a defined token without hex fallbacks', () => {
    const defined = new Set()
    for (const { css } of sources) {
      for (const match of css.matchAll(/--([a-z0-9-]+)\s*:/gi)) {
        defined.add(match[1].toLowerCase())
      }
    }
    const problems = []
    for (const { path, css } of sources) {
      for (const match of css.matchAll(/var\(\s*--([a-z0-9-]+)(\s*,\s*([^)]*))?\)/gi)) {
        const [, name, , fallback] = match
        if (!defined.has(name.toLowerCase())) {
          problems.push(`${path}: undefined token --${name}`)
        }
        if (fallback && /#(?:[0-9a-f]{3,8})\b/i.test(fallback.trim())) {
          problems.push(`${path}: hex fallback var(--${name}, ${fallback.trim()})`)
        }
      }
    }
    expect(problems).toEqual([])
  })

  it('keeps data-theme / data-density overrides out of migrated pages', () => {
    const allowed = new Set([
      'src/styles/variables.css',
      'src/styles/base.css'
    ])
    const offenders = sources
      .filter(({ css }) => /\[data-(theme|density)/.test(css))
      .map(({ path }) => path)
      .filter((path) => !allowed.has(path))
    expect(offenders).toEqual([])
  })

  it('uses only the seven semantic z-index tokens outside the token source', () => {
    const allowed = new Set(['raised', 'sticky', 'nav', 'overlay', 'popover', 'modal', 'toast'])
    const problems = []
    for (const { path, css } of sources) {
      if (path === 'src/styles/variables.css') continue
      for (const match of css.matchAll(/z-index\s*:\s*([^;]+);/gi)) {
        const value = match[1].trim().toLowerCase()
        if (value === '0') continue
        const token = value.match(/^var\(--z-([a-z]+)\)$/)?.[1]
        if (!token || !allowed.has(token)) {
          problems.push(`${path}: z-index: ${match[1].trim()}`)
        }
      }
    }
    expect(problems).toEqual([])
  })

  it('guards reduced motion in the global base styles', () => {
    const base = read('src/styles/base.css')
    expect(base).toContain('@media (prefers-reduced-motion: reduce)')
    const guard = cssBlock(base, '@media (prefers-reduced-motion: reduce)')
    expect(guard).toContain('animation-duration')
    expect(guard).toContain('transition-duration')
  })

  it('keeps the pre-paint theme bootstrap aligned with the light / dark / system contract', () => {
    const bootstrap = read('public/theme-bootstrap.js')
    expect(bootstrap).toContain('(prefers-color-scheme: dark)')
    expect(bootstrap).toContain("'system'")
    expect(bootstrap).toContain('localStorage.getItem(\'community.ui\')')
  })
})

describe('primitive class guardrails', () => {
  // .input / .auth-field / .field-label / .auth-form 是原语内部实现细节（规范 5.3），
  // 视图随页面簇迁移收敛到 UiInput / UiTextarea / UiField。这里登记现状基线，只减不增：
  // 新增使用或计数上升视为泄漏；完成迁移的视图应随迁移 PR 下调或移除基线条目。
  const classBaseline = new Map([
    ['src/views/AnalyticsView.vue', { input: 2 }],
    ['src/views/DriveShareView.vue', { input: 1 }],
    ['src/views/DriveView.vue', { input: 5 }],
    ['src/views/MarketDetailView.vue', { input: 1 }],
    ['src/views/MarketOrderDetailView.vue', { input: 6 }],
    ['src/views/MarketPublishView.vue', { input: 3 }],
    ['src/views/ModerationView.vue', { input: 5 }],
    ['src/views/UserManagementView.vue', { 'field-label': 3, input: 5 }],
    ['src/views/WalletAdminView.vue', { input: 4 }],
    ['src/views/WalletView.vue', { input: 4 }]
  ])

  function collectViewClassUsage() {
    const root = resolve(process.cwd(), 'src/views')
    const files = []
    const walk = (dir) => {
      for (const entry of readdirSync(dir, { withFileTypes: true })) {
        const full = resolve(dir, entry.name)
        if (entry.isDirectory()) {
          walk(full)
        } else if (entry.name.endsWith('.vue')) {
          files.push(full)
        }
      }
    }
    walk(root)
    const usage = new Map()
    for (const full of files) {
      const raw = readFileSync(full, 'utf8')
      const rel = `src/views/${full.slice(root.length + 1)}`
      const counts = {}
      let input = 0
      for (const match of raw.matchAll(/class="([^"]*)"/g)) {
        input += match[1].split(/\s+/).filter((token) => token === 'input').length
      }
      if (input) counts.input = input
      for (const name of ['auth-field', 'field-label', 'auth-form']) {
        const found = raw.match(new RegExp(`\\b${name}\\b`, 'g'))
        if (found) counts[name] = found.length
      }
      if (Object.keys(counts).length) usage.set(rel, counts)
    }
    return usage
  }

  it('does not spread primitive-internal classes into more views', () => {
    const usage = collectViewClassUsage()
    const problems = []
    for (const [path, counts] of usage) {
      const baseline = classBaseline.get(path) || {}
      for (const [name, count] of Object.entries(counts)) {
        const allowed = baseline[name] || 0
        if (count > allowed) {
          problems.push(`${path}: .${name} ${allowed} -> ${count}`)
        }
      }
    }
    expect(problems).toEqual([])
  })

  it('keeps a visible focus ring on the form primitives through the --focus-ring token', () => {
    const sources = collectStyleSources()
    for (const path of ['src/components/ui/UiInput.vue', 'src/components/ui/UiTextarea.vue']) {
      const source = sources.find((item) => item.path === path)
      expect(source, path).toBeTruthy()
      expect(source.css, `${path} :focus-visible`).toContain(':focus-visible')
      expect(source.css, `${path} --focus-ring`).toContain('var(--focus-ring)')
    }
  })
})
