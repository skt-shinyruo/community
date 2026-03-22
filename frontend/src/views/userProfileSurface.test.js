import { describe, expect, it } from 'vitest'
import { buildCommunitySignals, buildCommunityNextSteps } from './userProfileSurface'

describe('userProfileSurface', () => {
  it('builds a self-view summary with editable next steps', () => {
    const signals = buildCommunitySignals({
      profile: { username: 'Mara', likeCount: 12, followerCount: 8, followeeCount: 5, score: 320 },
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
    expect(signals[1].value).toContain('12')
    expect(signals[2].value).toContain('8')

    const nextSteps = buildCommunityNextSteps({ authed: true, isSelf: true })
    expect(nextSteps.map((item) => item.label)).toEqual(['编辑资料', '回到讨论区', '查看排行榜'])
  })

  it('builds an other-user summary with follow context and degraded fallback', () => {
    const signals = buildCommunitySignals({
      profile: { username: 'Lin', likeCount: 0, followerCount: 0, followeeCount: 0, score: 88 },
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
      label: '社区影响',
      value: '统计暂不可用'
    })
    expect(signals[2].text).toContain('稍后刷新')

    const nextSteps = buildCommunityNextSteps({ authed: true, isSelf: false })
    expect(nextSteps.map((item) => item.label)).toEqual(['去讨论区看看', '查看关注', '查看粉丝'])
  })
})
