import { describe, expect, it } from 'vitest'
import { buildCommunitySignals, buildCommunityNextSteps, describeFollowStatusText } from './userProfileSurface'

describe('userProfileSurface', () => {
  it('builds a self-view summary with wallet-first next steps', () => {
    const signals = buildCommunitySignals({
      profile: { username: 'Mara', likeCount: 12, followerCount: 8, followeeCount: 5, walletBalance: 420 },
      joinedYear: '2024',
      socialDegraded: false,
      followStatus: null,
      authed: true,
      isSelf: true
    })

    expect(signals[0]).toMatchObject({
      label: '当前状态',
      value: '这是你的主页'
    })
    expect(signals[1]).toMatchObject({
      label: '钱包资产',
      value: '420 积分'
    })
    expect(signals[2].value).toContain('8')

    const nextSteps = buildCommunityNextSteps({ authed: true, isSelf: true })
    expect(nextSteps.map((item) => item.label)).toEqual(['编辑资料', '回到讨论区', '查看钱包'])
  })

  it('builds an other-user summary with wallet copy and degraded network fallback', () => {
    const signals = buildCommunitySignals({
      profile: { username: 'Lin', likeCount: 0, followerCount: 0, followeeCount: 0, walletBalance: 88 },
      joinedYear: '2023',
      socialDegraded: true,
      followStatus: true,
      authed: true,
      isSelf: false
    })

    expect(signals[0]).toMatchObject({
      label: '当前状态',
      value: '你已关注'
    })
    expect(signals[1]).toMatchObject({
      label: '钱包资产',
      value: '88 积分'
    })
    expect(signals[2].text).toContain('稍后刷新')

    const nextSteps = buildCommunityNextSteps({ authed: true, isSelf: false })
    expect(nextSteps.map((item) => item.label)).toEqual(['去讨论区看看', '查看关注', '查看粉丝'])
  })

  it('describes follow status for self, followed, available, and anonymous states', () => {
    expect(describeFollowStatusText({ authed: true, isSelf: true, followStatus: null })).toBe('这是你的主页')
    expect(describeFollowStatusText({ authed: true, isSelf: false, followStatus: true })).toBe('你已关注')
    expect(describeFollowStatusText({ authed: true, isSelf: false, followStatus: false, followStatusState: 'ready' })).toBe('公开可关注')
    expect(describeFollowStatusText({ authed: true, isSelf: false, followStatus: null, followStatusState: 'loading' })).toBe('关系查询中')
    expect(describeFollowStatusText({ authed: true, isSelf: false, followStatus: null, followStatusState: 'error' })).toBe('关系暂不可用')
    expect(describeFollowStatusText({ authed: false, isSelf: false, followStatus: null })).toBe('公开可见')
  })
})
