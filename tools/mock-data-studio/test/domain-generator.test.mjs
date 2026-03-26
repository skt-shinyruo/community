import assert from 'node:assert/strict'
import test from 'node:test'

import { generateDomainPhaseDataset, generateImPhaseDataset } from '../src/generator/domainGenerator.mjs'
import { buildScenePresets } from '../src/generator/scenes/defaults.mjs'

function createPlan({
  batchId = 42,
  sceneKey = 'tech-community-hot-start',
  communityDeficits = {},
  imDeficits = {},
  growthDeficits = {},
  moderationDeficits = {},
  rewardDeficits = {}
} = {}) {
  return {
    batchId,
    sceneKey,
    phases: [
      {
        name: 'community',
        deficits: {
          messages: 0,
          notices: 0,
          ...communityDeficits
        }
      },
      {
        name: 'im',
        deficits: {
          im_rooms: 0,
          im_room_members: 0,
          im_room_messages: 0,
          im_conversations: 0,
          im_private_messages: 0,
          ...imDeficits
        }
      },
      {
        name: 'growth',
        deficits: {
          growth_check_ins: 0,
          user_task_progress: 0,
          reward_accounts: 0,
          reward_ledgers: 0,
          reward_grant_records: 0,
          ...growthDeficits
        }
      },
      {
        name: 'moderation',
        deficits: {
          reports: 0,
          moderation_actions: 0,
          ...moderationDeficits
        }
      },
      {
        name: 'reward',
        deficits: {
          reward_items: 0,
          reward_orders: 0,
          ...rewardDeficits
        }
      }
    ]
  }
}

test('generateImPhaseDataset keeps room members unique inside each room when users are scarce', () => {
  const dataset = generateImPhaseDataset({
    plan: createPlan({
      imDeficits: {
        im_rooms: 3,
        im_room_members: 9,
        im_room_messages: 6
      }
    }),
    existing: {
      users: [{ id: 1 }, { id: 2 }]
    },
    seed: 'small-im-pool'
  })

  assert.equal(dataset.roomMembers.length, 6)

  const membersByRoom = new Map()
  for (const member of dataset.roomMembers) {
    const roomMembers = membersByRoom.get(member.roomId) ?? new Set()
    roomMembers.add(member.userId)
    membersByRoom.set(member.roomId, roomMembers)
  }

  for (const room of dataset.rooms) {
    const memberIds = membersByRoom.get(room.roomId) ?? new Set()
    assert.equal(memberIds.size, 2)
  }

  for (const message of dataset.roomMessages) {
    assert.ok((membersByRoom.get(message.roomId) ?? new Set()).has(message.fromUserId))
  }
})

test('generateDomainPhaseDataset avoids cross-batch refs and global uniqueness collisions', () => {
  const dataset = generateDomainPhaseDataset({
    plan: createPlan({
      growthDeficits: {
        growth_check_ins: 6,
        user_task_progress: 8
      },
      moderationDeficits: {
        moderation_actions: 2
      },
      rewardDeficits: {
        reward_orders: 3
      }
    }),
    existing: {
      users: [{ id: 1 }, { id: 2 }],
      posts: [{ id: 11 }],
      comments: [{ id: 21, postId: 11, userId: 1 }],
      reports: [{ id: 301, reporterId: 1, targetType: 1, targetId: 11 }],
      batchReports: [],
      growthCheckIns: [
        { userId: 1, bizDate: '2026-03-01' },
        { userId: 2, bizDate: '2026-03-02' }
      ],
      userTaskProgress: [
        { userId: 1, taskCode: 'DAILY_CHECK_IN', periodKey: '2026-03-01' },
        { userId: 2, taskCode: 'WEEKLY_COMMENTER', periodKey: '2026-W10' }
      ],
      rewardAccounts: [],
      rewardItems: [{ id: 901 }],
      batchRewardItems: []
    },
    seed: 'domain-integrity'
  })

  assert.ok(dataset.moderationActions.every((action) => action.reportRef == null))
  assert.equal(dataset.rewardOrders.length, 0)

  const growthKeys = new Set()
  for (const entry of dataset.growthCheckIns) {
    const key = `${entry.userId}:${entry.bizDate}`
    assert.equal(growthKeys.has(key), false)
    assert.notEqual(key, '1:2026-03-01')
    assert.notEqual(key, '2:2026-03-02')
    growthKeys.add(key)
  }

  const taskKeys = new Set()
  for (const entry of dataset.userTaskProgress) {
    const key = `${entry.userId}:${entry.taskCode}:${entry.periodKey}`
    assert.equal(taskKeys.has(key), false)
    assert.notEqual(key, '1:DAILY_CHECK_IN:2026-03-01')
    assert.notEqual(key, '2:WEEKLY_COMMENTER:2026-W10')
    taskKeys.add(key)
  }
})

test('buildScenePresets caps small-user phase-2 targets to feasible limits', () => {
  const presets = buildScenePresets({
    defaults: {
      users: 2,
      posts: 10,
      comments: 20
    }
  })

  const hotStartTargets = Object.fromEntries(
    presets['tech-community-hot-start'].targets.map((target) => [target.entityType, target.targetCount])
  )
  const imBusyTargets = Object.fromEntries(
    presets['im-busy'].targets.map((target) => [target.entityType, target.targetCount])
  )
  const rewardBusyTargets = Object.fromEntries(
    presets['reward-ops-busy'].targets.map((target) => [target.entityType, target.targetCount])
  )

  assert.equal(hotStartTargets.im_conversations, 1)
  assert.equal(hotStartTargets.reward_accounts, 2)
  assert.equal(hotStartTargets.growth_check_ins, 56)
  assert.equal(hotStartTargets.user_task_progress, 122)
  assert.equal(imBusyTargets.im_conversations, 1)
  assert.equal(imBusyTargets.im_room_members, 32)
  assert.equal(rewardBusyTargets.growth_check_ins, 56)
})
