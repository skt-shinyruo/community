export function buildProfileWalletAsset({ authed, isSelf } = {}) {
  if (authed && isSelf) {
    return {
      valueText: '仅自己可见',
      chipText: '仅自己可见',
      description: '资产明细只在钱包页向本人展示。'
    }
  }

  return {
    valueText: '未公开',
    chipText: '未公开',
    description: '该成员未公开资产信息。'
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
