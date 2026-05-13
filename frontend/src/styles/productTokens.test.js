import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

function read(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

function cssBlock(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`, 's'))
  return match?.[1] || ''
}

describe('product design token guardrails', () => {
  it('uses restrained radius and shadow tokens', () => {
    const variables = read('src/styles/variables.css')

    expect(variables).toContain('--radius-md: 12px;')
    expect(variables).toContain('--radius-lg: 12px;')
    expect(variables).toContain('--radius-xl: 16px;')
    expect(variables).not.toContain('--radius-lg: 24px')
    expect(variables).not.toContain('--radius-xl: 28px')
    expect(variables).toContain('--pending:')
    expect(variables).toContain('--unread:')
    expect(variables).not.toContain('warm editorial by default')
    expect(variables).not.toContain('public editorial shell')
  })

  it('keeps routine cards and buttons out of editorial styling', () => {
    const components = read('src/styles/components.css')
    const card = cssBlock(components, '.card')
    const button = cssBlock(components, '.btn')

    expect(card).toContain('border-radius: var(--radius-md)')
    expect(card).toContain('box-shadow: none')
    expect(card).not.toContain('transform')
    expect(button).toContain('border-radius: var(--radius-md)')
  })

  it('keeps mobile navigation compact for five high-frequency entries', () => {
    const mobileNav = read('src/components/layout/MobileNav.vue')

    expect(mobileNav).toContain('repeat(5, minmax(0, 1fr))')
    expect(mobileNav).toContain("item.icon === 'bell'")
    expect(mobileNav).toContain("item.icon === 'messages'")
  })

  it('defines semantic pending and unread badge styling', () => {
    const components = read('src/styles/components.css')

    expect(components).toContain('.badge-pending')
    expect(components).toContain('.badge-unread')
  })
})
