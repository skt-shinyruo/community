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
const retiredLikeCountToken = 'export async function getLikeCount('
const retiredLikeStatusToken = 'export async function getLikeStatus('
const retiredNormalizePostsTagToken = 'export function normalizePostsTag('
const retiredGrowthViewOne = ['views/', 'Sign', 'In', 'Calendar', 'View.vue'].join('')
const retiredGrowthViewTwo = ['views/', 'Task', 'Center', 'View.vue'].join('')
const retiredMarketDisputeWrapper = ['open', 'Market', 'Dispute'].join('')
const retiredMarketAcceptWrapper = ['seller', 'Accept', 'Market', 'Dispute'].join('')
const retiredMarketRejectWrapper = ['seller', 'Reject', 'Market', 'Dispute'].join('')
const retiredSubscriptionSubscribeWrapper = ['subscribe', 'Category'].join('')
const retiredSubscriptionUnsubscribeWrapper = ['unsubscribe', 'Category'].join('')
const retiredUiTagView = ['components/ui/', 'UiTag.vue'].join('')
const retiredUiEmptyView = ['components/ui/', 'UiEmpty.vue'].join('')
const retiredUiEmptyImport = ['components/ui/', 'UiEmpty.vue'].join('')
const retiredSearchReindexRoute = ['/api/search/internal', '/reindex'].join('')
const retiredSearchOpsReindexRoute = ['/api/ops/search', '/reindex'].join('')
const retiredSearchReindexWrapper = ['re', 'index'].join('')
const retiredOpsConsoleView = ['views/', 'Ops', 'Console', 'View.vue'].join('')
const retiredOpsConsoleRouteName = ['ops', 'Console'].join('')
const retiredLeaderboardCopy = '排行榜'
const retiredRewardBackendCopy = '旧奖励后台'
const retiredRightPanelCopy = '右侧面板'

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
    expect(searchService).not.toContain(retiredSearchOpsReindexRoute)
    expect(searchService).not.toContain(retiredSearchReindexWrapper)
  })

  it('keeps retired navigation helpers removed', () => {
    const navigation = source('router/navigation.js')

    expect(navigation).not.toContain(retiredNavQueryToken)
    expect(navigation).not.toContain(retiredNavBuildToken)
    expect(navigation).not.toContain(retiredNormalizePostsTagToken)
  })

  it('keeps retired growth views unmounted', () => {
    expect(exists(retiredGrowthViewOne)).toBe(false)
    expect(exists(retiredGrowthViewTwo)).toBe(false)
  })

  it('keeps retired search reindex operation surfaces removed', () => {
    expect(exists(retiredOpsConsoleView)).toBe(false)
    expect(source('router/index.js')).not.toContain(retiredOpsConsoleRouteName)
    expect(source('router/navigation.js')).not.toContain(retiredOpsConsoleRouteName)
    expect(source('views/SearchView.vue')).not.toContain(retiredSearchReindexWrapper)
    expect(source('views/SearchView.vue')).not.toContain(retiredSearchOpsReindexRoute)
  })

  it('keeps retired market and subscription wrappers removed', () => {
    const marketService = source('api/services/marketService.js')
    const subscriptionService = source('api/services/subscriptionService.js')
    const socialService = source('api/services/socialService.js')

    expect(marketService).not.toContain(retiredMarketDisputeWrapper)
    expect(marketService).not.toContain(retiredMarketAcceptWrapper)
    expect(marketService).not.toContain(retiredMarketRejectWrapper)

    expect(subscriptionService).not.toContain(retiredSubscriptionSubscribeWrapper)
    expect(subscriptionService).not.toContain(retiredSubscriptionUnsubscribeWrapper)

    expect(socialService).not.toContain(retiredLikeCountToken)
    expect(socialService).not.toContain(retiredLikeStatusToken)
  })

  it('keeps retired UiTag component removed', () => {
    expect(exists(retiredUiTagView)).toBe(false)
  })

  it('keeps retired UiEmpty wrapper removed', () => {
    expect(exists(retiredUiEmptyView)).toBe(false)
    expect(source('views/PostsView.vue')).not.toContain(retiredUiEmptyImport)
  })

  it('keeps retired product copy out of active frontend sources', () => {
    expect(source('views/SettingsView.vue')).not.toContain(retiredLeaderboardCopy)
    expect(source('views/AdminMarketDisputesView.vue')).not.toContain(retiredRewardBackendCopy)
    expect(source('stores/taxonomy.js')).not.toContain(retiredRightPanelCopy)
  })
})
