// @vitest-environment node

import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const srcRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const retiredAuthVerifyWrapper = ['verify', 'Captcha'].join('')
const retiredAuthVerifyRoute = ['/api/auth/captcha', '/verify'].join('')

function source(relativePath) {
  return readFileSync(resolve(srcRoot, relativePath), 'utf8')
}

function exists(relativePath) {
  return existsSync(resolve(srcRoot, relativePath))
}

describe('unused surface retirement', () => {
  it('keeps retired service wrappers and route strings removed', () => {
    const authService = source('api/services/authService.js')
    const blockService = source('api/services/blockService.js')
    const userService = source('api/services/userService.js')
    const socialService = source('api/services/socialService.js')
    const searchService = source('api/services/searchService.js')

    expect(authService).not.toContain(retiredAuthVerifyWrapper)
    expect(authService).not.toContain(retiredAuthVerifyRoute)

    expect(blockService).not.toContain('getBlockStatus')
    expect(blockService).not.toContain('/api/blocks/status')

    expect(userService).not.toContain('resolveUserByUsername')
    expect(userService).not.toContain('/api/users/resolve')

    expect(socialService).not.toContain('getUserLikeCount')
    expect(socialService).not.toContain('countFollowees')
    expect(socialService).not.toContain('countFollowers')
    expect(socialService).not.toContain('/api/likes/users/')

    expect(searchService).not.toContain('/api/search/internal/reindex')
  })

  it('keeps retired navigation helpers removed', () => {
    const navigation = source('router/navigation.js')

    expect(navigation).not.toContain('normalizePostsQuery')
    expect(navigation).not.toContain('buildPostsQuery')
  })

  it('keeps retired growth views unmounted', () => {
    expect(exists('views/SignInCalendarView.vue')).toBe(false)
    expect(exists('views/TaskCenterView.vue')).toBe(false)
  })
})
