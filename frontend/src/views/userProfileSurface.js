function toCount(value) {
  const count = Number(value || 0)
  return Number.isFinite(count) && count > 0 ? count : 0
}

export function buildProfileWalletAsset({ profile, authed, isSelf } = {}) {
  if (authed && isSelf) {
    return {
      valueText: '钱包页为准',
      chipText: '钱包页为准',
      description: '当前主页还未接入真实钱包余额，请以钱包页里的最新余额为准。'
    }
  }

  return {
    valueText: '暂未公开',
    chipText: '暂未公开',
    description: '该成员的钱包资产暂未在主页公开展示，请以后续资产页或钱包页为准。'
  }
}

export function describeFollowStatusText({ followStatus, followStatusState = 'idle', authed, isSelf } = {}) {
  if (isSelf) return '这是你的主页'
  if (followStatus === true) return '你已关注'
  if (authed) {
    if (followStatus === false && followStatusState === 'ready') return '公开可关注'
    if (followStatusState === 'error') return '关系暂不可用'
    return '关系查询中'
  }
  return '公开可见'
}

export function buildCommunitySignals({
  profile,
  joinedYear,
  followStatus,
  followStatusState,
  authed,
  isSelf
} = {}) {
  const username = profile?.username || '该成员'
  const joined = joinedYear || '—'
  const followerCount = toCount(profile?.followerCount)
  const followeeCount = toCount(profile?.followeeCount)
  const walletAsset = buildProfileWalletAsset({ profile, authed, isSelf })

  const statusValue = describeFollowStatusText({ followStatus, followStatusState, authed, isSelf })

  return [
    {
      key: 'status',
      label: '当前状态',
      value: statusValue,
      text: `${username} 于 ${joined} 加入社区，当前主页优先展示公开身份与关系线索。`
    },
    {
      key: 'wallet',
      label: '钱包资产',
      value: walletAsset.valueText,
      text: formatWalletText(walletAsset.description, username)
    },
    {
      key: 'network',
      label: '关系网络',
      value: `${followeeCount} 关注 · ${followerCount} 粉丝`,
      text: '通过关注与粉丝可以快速判断这个成员在社区中的连接范围。'
    }
  ]
}

export function buildCommunityNextSteps({ authed, isSelf, userId } = {}) {
  if (authed && isSelf) {
    return [
      { key: 'settings', label: '编辑资料', to: { name: 'settings' }, variant: 'secondary' },
      { key: 'posts', label: '回到讨论区', to: { name: 'posts' }, variant: 'ghost' },
      { key: 'wallet', label: '查看钱包', to: { name: 'wallet' }, variant: 'ghost' }
    ]
  }

  return [
    { key: 'posts', label: '去讨论区看看', to: { name: 'posts' }, variant: 'secondary' },
    { key: 'followees', label: '查看关注', to: { name: 'followees', params: { userId: String(userId || '') } }, variant: 'ghost' },
    { key: 'followers', label: '查看粉丝', to: { name: 'followers', params: { userId: String(userId || '') } }, variant: 'ghost' }
  ]
}

function formatWalletText(text, username) {
  return text.replace(/^主页资产展示/, `${username} 的主页资产展示`)
}
