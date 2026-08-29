import { describe, expect, it } from 'vitest'
import {
  buildCommunityNextSteps,
  buildProfileWalletAsset,
  describeFollowStatusText
} from './useUserProfilePage'

describe('useUserProfilePage surface builders', () => {
  it('builds self and public profile next steps', () => {
    expect(buildCommunityNextSteps({ authed: true, isSelf: true }).map((item) => item.label))
      .toEqual(['编辑资料', '回到讨论区', '查看钱包'])
    expect(buildCommunityNextSteps({ authed: true, isSelf: false }).map((item) => item.label))
      .toEqual(['去讨论区看看', '查看关注', '查看粉丝'])
  })

  it('does not derive self-view wallet copy from profile snapshots', () => {
    expect(buildProfileWalletAsset({ profile: { walletBalance: 999 }, authed: true, isSelf: true })).toMatchObject({
      valueText: '仅自己可见',
      chipText: '仅自己可见'
    })

    expect(buildProfileWalletAsset({ profile: { walletBalance: 28 }, authed: false, isSelf: false })).toMatchObject({
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
