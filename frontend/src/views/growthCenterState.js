const GROUP_META = Object.freeze([
  {
    key: 'daily',
    periodType: 'DAILY',
    title: '每日任务',
    description: '把高频动作收拢成今天就能完成的节奏。'
  },
  {
    key: 'weekly',
    periodType: 'WEEKLY',
    title: '每周任务',
    description: '比日常再长一点，鼓励持续输出而不是一次性冲刺。'
  },
  {
    key: 'lifetime',
    periodType: 'LIFETIME',
    title: '长期任务',
    description: '累计型目标更适合映射长期贡献和创作者成长。'
  }
])

const TASK_COPY = Object.freeze({
  DAILY_CHECK_IN: {
    title: '每日签到',
    description: '每天回到社区打卡一次，维持稳定的活跃节奏。'
  },
  DAILY_POST: {
    title: '今日发帖',
    description: '发起一条新的讨论，让社区里出现新的话题入口。'
  },
  WEEKLY_COMMENTER: {
    title: '本周评论',
    description: '参与回复与讨论，把内容互动真正延展开来。'
  },
  LIFETIME_RECEIVE_LIKE: {
    title: '累计获赞',
    description: '长期积累被认可的内容，慢慢建立公开影响力。'
  }
})

function asNumber(value, fallback = 0) {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

function normalizeTaskUiState(status) {
  if (status === 'CLAIMED') return 'claimed'
  if (status === 'CLAIMABLE') return 'claimable'
  return 'in-progress'
}

function taskCopy(taskCode) {
  return TASK_COPY[taskCode] || {
    title: taskCode || '任务',
    description: '完成后会获得一笔成长奖励。'
  }
}

function rewardText(item) {
  const growth = asNumber(item?.rewardGrowthDelta)
  const balance = asNumber(item?.rewardBalanceDelta)
  const parts = []
  if (growth > 0) parts.push(`+${growth} 成长值`)
  if (balance > 0) parts.push(`+${balance} 奖励积分`)
  return parts.join(' · ') || '无额外奖励'
}

function claimLabel(uiState) {
  if (uiState === 'claimed') return '已完成'
  if (uiState === 'claimable') return '可领取'
  return '进行中'
}

function mapTaskItem(item) {
  const currentValue = Math.max(0, asNumber(item?.currentValue))
  const targetValue = Math.max(1, asNumber(item?.targetValue, 1))
  const uiState = normalizeTaskUiState(item?.status)
  const copy = taskCopy(item?.taskCode)

  return {
    ...item,
    ...copy,
    currentValue,
    targetValue,
    uiState,
    claimLabel: claimLabel(uiState),
    progressText: `${currentValue} / ${targetValue}`,
    rewardText: rewardText(item)
  }
}

export function buildGrowthCenterState({ summary, checkInStatus, tasks } = {}) {
  const safeSummary = summary && typeof summary === 'object' ? summary : {}
  const safeStatus = checkInStatus && typeof checkInStatus === 'object' ? checkInStatus : {}
  const rawTasks = Array.isArray(tasks?.items) ? tasks.items : []

  const header = {
    score: asNumber(safeSummary.score),
    level: Math.max(1, asNumber(safeSummary.level, 1)),
    userLevel: Math.max(1, asNumber(safeSummary.userLevel, safeSummary.level || 1)),
    signInDaysInWindow: Math.max(
      0,
      asNumber(safeSummary.signInDaysInWindow, safeStatus.totalCheckInDays || 0)
    ),
    windowDays: Math.max(1, asNumber(safeSummary.windowDays, 100)),
    rewardBalance: asNumber(safeSummary.rewardBalance),
    frozenBalance: asNumber(safeSummary.frozenBalance),
    checkedInToday: safeStatus.checkedInToday === true,
    currentStreak: Math.max(0, asNumber(safeStatus.currentStreak)),
    maxStreak: Math.max(0, asNumber(safeStatus.maxStreak)),
    totalCheckInDays: Math.max(0, asNumber(safeStatus.totalCheckInDays))
  }

  header.heroText = `用户等级 LV ${header.userLevel} · 最近 ${header.windowDays} 天签到 ${header.signInDaysInWindow} 天`
  header.streakText = `连续签到 ${header.currentStreak} 天 · 历史最高 ${header.maxStreak} 天`
  header.checkInText = header.checkedInToday ? '今天已签到' : '今天还没签到'
  header.balanceText = `可用奖励 ${header.rewardBalance} · 冻结 ${header.frozenBalance}`

  const groups = GROUP_META.map((meta) => ({
    key: meta.key,
    title: meta.title,
    description: meta.description,
    items: rawTasks
      .filter((item) => item?.periodType === meta.periodType)
      .sort((a, b) => asNumber(a?.displayOrder) - asNumber(b?.displayOrder))
      .map(mapTaskItem)
  }))

  return {
    header,
    groups,
    bizDate: tasks?.bizDate || ''
  }
}
