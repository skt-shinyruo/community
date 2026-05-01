import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

function lineCount(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8').split('\n').length
}

describe('view complexity guardrails', () => {
  it('keeps post views split into focused modules', () => {
    expect(lineCount('src/views/PostDetailView.vue')).toBeLessThanOrEqual(900)
    expect(lineCount('src/views/PostsView.vue')).toBeLessThanOrEqual(900)
  })
})
