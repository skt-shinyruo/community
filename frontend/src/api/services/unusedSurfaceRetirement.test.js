// @vitest-environment node

import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const srcRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const retiredAuthVerifyWrapper = ['verify', 'Captcha'].join('')
const retiredAuthVerifyRoute = ['/api/auth/captcha', '/verify'].join('')
const retiredBlockStatusWrapper = ['get', 'Block', 'Status'].join('')
const retiredBlockStatusRoute = ['/api/blocks', '/status'].join('')
const retiredUserResolveWrapper = ['resolve', 'User', 'By', 'Username'].join('')
const retiredUserResolveRoute = ['/api/users', '/resolve'].join('')
const retiredUserLikesWrapper = ['get', 'User', 'Like', 'Count'].join('')
const retiredFolloweeCountWrapper = ['count', 'Followees'].join('')
const retiredFollowerCountWrapper = ['count', 'Followers'].join('')
const retiredUserLikesRoutePrefix = ['/api/likes', '/users/'].join('')
const retiredNavQueryToken = ['normalize', 'Posts', 'Query'].join('')
const retiredNavBuildToken = ['build', 'Posts', 'Query'].join('')
const retiredGrowthViewOne = ['views/', 'Sign', 'In', 'Calendar', 'View.vue'].join('')
const retiredGrowthViewTwo = ['views/', 'Task', 'Center', 'View.vue'].join('')
const retiredSearchReindexRoute = ['/api/search/internal', '/reindex'].join('')

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

    expect(blockService).not.toContain(retiredBlockStatusWrapper)
    expect(blockService).not.toContain(retiredBlockStatusRoute)

    expect(userService).not.toContain(retiredUserResolveWrapper)
    expect(userService).not.toContain(retiredUserResolveRoute)

    expect(socialService).not.toContain(retiredUserLikesWrapper)
    expect(socialService).not.toContain(retiredFolloweeCountWrapper)
    expect(socialService).not.toContain(retiredFollowerCountWrapper)
    expect(socialService).not.toContain(retiredUserLikesRoutePrefix)

    expect(searchService).not.toContain(retiredSearchReindexRoute)
  })

  it('keeps retired navigation helpers removed', () => {
    const navigation = source('router/navigation.js')

    expect(navigation).not.toContain(retiredNavQueryToken)
    expect(navigation).not.toContain(retiredNavBuildToken)
  })

  it('keeps retired growth views unmounted', () => {
    expect(exists(retiredGrowthViewOne)).toBe(false)
    expect(exists(retiredGrowthViewTwo)).toBe(false)
  })
})
