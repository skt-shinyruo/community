function toCount(value) {
  const count = Number(value || 0)
  return Number.isFinite(count) && count > 0 ? count : 0
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
  socialDegraded,
  followStatus,
  followStatusState,
  authed,
  isSelf
} = {}) {
  const username = profile?.username || '该成员'
  const joined = joinedYear || '—'
  const likeCount = toCount(profile?.likeCount)
  const followerCount = toCount(profile?.followerCount)
  const followeeCount = toCount(profile?.followeeCount)
  const score = toCount(profile?.score)

  const statusValue = describeFollowStatusText({ followStatus, followStatusState, authed, isSelf })

  return [
    {
      key: 'status',
      label: '当前状态',
      value: statusValue,
      text: `${username} 于 ${joined} 加入社区，当前主页优先展示公开身份与关系线索。`
    },
    {
      key: 'impact',
      label: '社区影响',
      value: socialDegraded ? '统计暂不可用' : `${likeCount} 获赞`,
      text: socialDegraded ? '社交统计暂时降级，稍后刷新后再看影响力变化。' : `当前积分 ${score} 分，获赞数反映这个成员公开内容被回应的强度。`
    },
    {
      key: 'network',
      label: '关系网络',
      value: socialDegraded ? '稍后刷新' : `${followeeCount} 关注 · ${followerCount} 粉丝`,
      text: socialDegraded ? '关注和粉丝数据暂不可用，稍后刷新可恢复关系网络视图。' : '通过关注与粉丝可以快速判断这个成员在社区中的连接范围。'
    }
  ]
}

export function buildCommunityNextSteps({ authed, isSelf, userId } = {}) {
  if (authed && isSelf) {
    return [
      { key: 'settings', label: '编辑资料', to: { name: 'settings' }, variant: 'secondary' },
      { key: 'posts', label: '回到讨论区', to: { name: 'posts' }, variant: 'ghost' },
      { key: 'leaderboard', label: '查看排行榜', to: { name: 'leaderboard' }, variant: 'ghost' }
    ]
  }

  return [
    { key: 'posts', label: '去讨论区看看', to: { name: 'posts' }, variant: 'secondary' },
    { key: 'followees', label: '查看关注', to: { name: 'followees', params: { userId: String(userId || '') } }, variant: 'ghost' },
    { key: 'followers', label: '查看粉丝', to: { name: 'followers', params: { userId: String(userId || '') } }, variant: 'ghost' }
  ]
}
