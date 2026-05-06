import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

function read(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

function lineCount(path) {
  return read(path).split('\n').length
}

describe('view complexity guardrails', () => {
  it('keeps post views split into focused modules', () => {
    expect(lineCount('src/views/PostDetailView.vue')).toBeLessThanOrEqual(900)
    expect(lineCount('src/views/PostsView.vue')).toBeLessThanOrEqual(900)
  })
})

describe('product redesign CSS guardrails', () => {
  it('does not keep public card styling as the global product default', () => {
    const layout = read('src/styles/layout.css')
    expect(layout).not.toContain('.app-shell--public .card')
    expect(layout).not.toContain('--editorial-shadow')
  })

  it('removes market hero selectors from page styles', () => {
    const pages = read('src/styles/pages.css')
    expect(pages).not.toContain('.market-hero')
    expect(pages).not.toContain('.market-hero-actions')
  })

  it('keeps routine card radii below large editorial treatment', () => {
    const components = read('src/styles/components.css')
    expect(components).not.toMatch(/\.card\s*\{[^}]*border-radius:\s*30px/s)
    expect(components).not.toMatch(/\.card\s*\{[^}]*border-radius:\s*var\(--radius-xl\)/s)
  })

  it('removes wallet and profile cover shells from the current view templates', () => {
    const wallet = read('src/views/WalletView.vue')
    const profile = read('src/views/UserProfileView.vue')
    const conversations = read('src/views/ConversationsView.vue')
    const notices = read('src/views/NoticesView.vue')
    const noticeDetail = read('src/views/NoticeDetailView.vue')
    const home = read('src/views/HomeView.vue')
    const register = read('src/views/RegisterView.vue')
    const passwordReset = read('src/views/PasswordResetView.vue')

    expect(wallet).not.toContain('class="wallet-hero"')
    expect(profile).not.toContain('class="profile-cover"')
    expect(profile).not.toContain('class="profile-cover-sheet"')
    expect(conversations).not.toContain('conversations-hero-label')
    expect(notices).not.toContain('notices-hero-label')
    expect(noticeDetail).not.toContain('notice-detail-hero-label')
    expect(noticeDetail).not.toContain('notice-detail-eyebrow')
    expect(home).toContain('variant="development"')
    expect(register).toContain('variant="development"')
    expect(passwordReset).toContain('variant="development"')
  })
})
