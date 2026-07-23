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

  it('keeps accent controls readable in both color schemes', () => {
    const variables = read('src/styles/variables.css')
    const components = read('src/styles/components.css')
    const composer = read('src/components/scene/ConversationComposer.vue')
    const conversation = read('src/views/ConversationDetailView.vue')
    const scrollTop = read('src/components/ui/UiScrollTop.vue')

    expect(variables).toContain('--accent-contrast: #ffffff;')
    expect(variables).toContain("--accent-contrast: #000000;")
    expect(cssBlock(components, '.btn')).toContain('color: var(--accent-contrast)')
    expect(composer).toContain('color: var(--accent-contrast)')
    expect(conversation).toContain('color: var(--accent-contrast)')
    expect(scrollTop).toContain('color: var(--accent-contrast)')
  })

  it('keeps mobile toasts above the fixed navigation', () => {
    const toast = read('src/components/ui/UiToast.vue')
    const mobileStyles = toast.slice(toast.indexOf('@media (max-width: 768px)'))
    const container = cssBlock(mobileStyles, '.toast-container')
    const item = cssBlock(mobileStyles, '.toast')

    expect(container).toContain('bottom: calc(96px + env(safe-area-inset-bottom, 0px))')
    expect(container).toContain('left: 14px')
    expect(container).toContain('right: 14px')
    expect(item).toContain('width: 100%')
  })
})
