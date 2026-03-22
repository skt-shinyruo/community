import { describe, expect, it } from 'vitest'
import { buildGrowthCenterState } from './growthCenterState'

describe('growthCenterState', () => {
  it('groups tasks by period and maps task ui states distinctly', () => {
    const state = buildGrowthCenterState({
      summary: { score: 320, level: 4, rewardBalance: 15, frozenBalance: 2 },
      checkInStatus: { checkedInToday: true, currentStreak: 3, maxStreak: 5, totalCheckInDays: 8 },
      tasks: {
        bizDate: '2026-03-22',
        items: [
          { taskCode: 'DAILY_CHECK_IN', taskType: 'CHECK_IN', periodType: 'DAILY', periodKey: '2026-03-22', currentValue: 1, targetValue: 1, status: 'CLAIMED', rewardGrowthDelta: 2, rewardBalanceDelta: 1, claimRequired: false, displayOrder: 10 },
          { taskCode: 'DAILY_POST', taskType: 'CONTENT', periodType: 'DAILY', periodKey: '2026-03-22', currentValue: 0, targetValue: 1, status: 'IN_PROGRESS', rewardGrowthDelta: 3, rewardBalanceDelta: 1, claimRequired: false, displayOrder: 20 },
          { taskCode: 'WEEKLY_COMMENTER', taskType: 'CONTENT', periodType: 'WEEKLY', periodKey: '2026-W12', currentValue: 2, targetValue: 2, status: 'CLAIMABLE', rewardGrowthDelta: 4, rewardBalanceDelta: 1, claimRequired: true, displayOrder: 30 },
          { taskCode: 'LIFETIME_RECEIVE_LIKE', taskType: 'SOCIAL', periodType: 'LIFETIME', periodKey: 'LIFETIME', currentValue: 2, targetValue: 3, status: 'IN_PROGRESS', rewardGrowthDelta: 6, rewardBalanceDelta: 2, claimRequired: false, displayOrder: 40 }
        ]
      }
    })

    expect(state.header).toMatchObject({
      score: 320,
      level: 4,
      rewardBalance: 15,
      frozenBalance: 2,
      currentStreak: 3,
      maxStreak: 5,
      checkedInToday: true
    })
    expect(state.groups.map((group) => group.key)).toEqual(['daily', 'weekly', 'lifetime'])
    expect(state.groups[0].items.map((item) => item.taskCode)).toEqual(['DAILY_CHECK_IN', 'DAILY_POST'])
    expect(state.groups[0].items[0]).toMatchObject({
      taskCode: 'DAILY_CHECK_IN',
      uiState: 'claimed',
      progressText: '1 / 1'
    })
    expect(state.groups[0].items[1]).toMatchObject({
      taskCode: 'DAILY_POST',
      uiState: 'in-progress',
      progressText: '0 / 1'
    })
    expect(state.groups[1].items[0]).toMatchObject({
      taskCode: 'WEEKLY_COMMENTER',
      uiState: 'claimable',
      claimLabel: '可领取'
    })
  })

  it('builds a compact header summary even when tasks are empty', () => {
    const state = buildGrowthCenterState({
      summary: { score: 0, level: 1, rewardBalance: 0, frozenBalance: 0 },
      checkInStatus: { checkedInToday: false, currentStreak: 0, maxStreak: 2, totalCheckInDays: 3 },
      tasks: { bizDate: '2026-03-22', items: [] }
    })

    expect(state.header.heroText).toContain('LV 1')
    expect(state.header.heroText).toContain('0 积分')
    expect(state.header.streakText).toContain('连续签到 0 天')
    expect(state.groups).toHaveLength(3)
    expect(state.groups.every((group) => Array.isArray(group.items))).toBe(true)
  })
})
