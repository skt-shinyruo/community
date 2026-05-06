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

  it('keeps routine card radii below large editorial treatment', () => {
    const components = read('src/styles/components.css')
    expect(components).not.toMatch(/\.card\s*\{[^}]*border-radius:\s*30px/s)
    expect(components).not.toMatch(/\.card\s*\{[^}]*border-radius:\s*var\(--radius-xl\)/s)
  })
})
