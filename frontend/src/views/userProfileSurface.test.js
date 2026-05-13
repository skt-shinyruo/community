import { describe, expect, it } from 'vitest'
import {
  buildCommunitySignals,
  buildCommunityNextSteps,
  buildProfileWalletAsset,
  describeFollowStatusText
} from './userProfileSurface'

describe('userProfileSurface', () => {
  it('builds a self-view summary that points wallet assets to the wallet page', () => {
    const signals = buildCommunitySignals({
      profile: { username: 'Mara', likeCount: 12, followerCount: 8, followeeCount: 5, score: 320 },
      joinedYear: '2024',
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
      value: '仅自己可见'
    })
    expect(signals[1].text).toBe('资产明细只在钱包页向本人展示。')
    expect(signals[2].value).toContain('8')

    const nextSteps = buildCommunityNextSteps({ authed: true, isSelf: true })
    expect(nextSteps.map((item) => item.label)).toEqual(['编辑资料', '回到讨论区', '查看钱包'])
  })

  it('avoids fake zero wallet copy when no asset snapshot exists', () => {
    const signals = buildCommunitySignals({
      profile: { username: 'Lin', likeCount: 0, followerCount: 0, followeeCount: 0, score: 0 },
      joinedYear: '2023',
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
      value: '未公开'
    })
    expect(signals[1].text).toBe('该成员未公开资产信息。')
    expect(signals[2].value).toBe('0 关注 · 0 粉丝')

    const nextSteps = buildCommunityNextSteps({ authed: true, isSelf: false })
    expect(nextSteps.map((item) => item.label)).toEqual(['去讨论区看看', '查看关注', '查看粉丝'])
  })

  it('does not derive self-view wallet copy from profile snapshots', () => {
    expect(buildProfileWalletAsset({ profile: { score: 999 }, authed: true, isSelf: true })).toMatchObject({
      valueText: '仅自己可见',
      chipText: '仅自己可见'
    })

    expect(buildProfileWalletAsset({ profile: { score: 28 }, authed: false, isSelf: false })).toMatchObject({
      valueText: '未公开'
    })
  })

  it('does not expose wallet implementation caveats in profile signals', () => {
    const asset = buildProfileWalletAsset({ authed: true, isSelf: true })

    expect(asset.valueText).toBe('仅自己可见')
    expect(asset.chipText).toBe('仅自己可见')
    expect(asset.description).toBe('资产明细只在钱包页向本人展示。')
    expect(asset.description).not.toContain('未接入')
    expect(asset.description).not.toContain('钱包页为准')
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
