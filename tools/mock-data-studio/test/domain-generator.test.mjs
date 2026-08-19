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
  moderationDeficits = {}
} = {}) {
  return {
    batchId,
    sceneKey,
    phases: [
      {
        name: 'community',
        deficits: communityDeficits
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
          user_task_progress: 0,
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
      }
    ]
  }
}

test('generateImPhaseDataset applies room member minimums and keeps members unique', () => {
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

  const minimumDataset = generateImPhaseDataset({
    plan: createPlan({
      imDeficits: {
        im_rooms: 3,
        im_room_members: 3
      }
    }),
    existing: {
      users: [{ id: 1 }, { id: 2 }, { id: 3 }]
    },
    seed: 'minimum-room-members'
  })
  for (const room of minimumDataset.rooms) {
    assert.equal(minimumDataset.roomMembers.filter((member) => member.roomId === room.roomId).length, 1)
  }
})

test('generateImPhaseDataset keeps deterministic message ids and participant relationships', () => {
  const input = {
    plan: createPlan({
      imDeficits: {
        im_rooms: 3,
        im_room_members: 7,
        im_room_messages: 8,
        im_conversations: 3,
        im_private_messages: 7
      }
    }),
    existing: {
      users: [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }, { id: 5 }]
    },
    seed: 'deterministic-im'
  }

  const dataset = generateImPhaseDataset(input)
  assert.deepEqual(generateImPhaseDataset(input), dataset)
  assert.deepEqual(
    {
      rooms: dataset.rooms.length,
      roomMembers: dataset.roomMembers.length,
      roomMessages: dataset.roomMessages.length,
      conversations: dataset.conversations.length,
      privateMessages: dataset.privateMessages.length
    },
    {
      rooms: 3,
      roomMembers: 7,
      roomMessages: 8,
      conversations: 3,
      privateMessages: 7
    }
  )
  assert.deepEqual(dataset.roomMessages.map((message) => message.messageId), [
    420000001, 420000002, 420000003, 420000004, 420000005, 420000006, 420000007, 420000008
  ])
  assert.deepEqual(dataset.privateMessages.map((message) => message.messageId), [
    840000001, 840000002, 840000003, 840000004, 840000005, 840000006, 840000007
  ])

  for (const message of dataset.roomMessages) {
    assert.ok(dataset.roomMembers.some(
      (member) => member.roomId === message.roomId && member.userId === message.fromUserId
    ))
  }
  for (const message of dataset.privateMessages) {
    const conversation = dataset.conversations.find(
      (candidate) => candidate.conversationId === message.conversationId
    )
    assert.deepEqual(new Set([message.fromUserId, message.toUserId]), new Set([conversation.userA, conversation.userB]))
  }
})

test('generateDomainPhaseDataset avoids cross-batch refs and global uniqueness collisions', () => {
  const dataset = generateDomainPhaseDataset({
    plan: createPlan({
      growthDeficits: {
        user_task_progress: 8
      },
      moderationDeficits: {
        moderation_actions: 2
      }
    }),
    existing: {
      users: [{ id: 1 }, { id: 2 }],
      posts: [{ id: 11 }],
      comments: [{ id: 21, postId: 11, userId: 1 }],
      reports: [{ id: 301, reporterId: 1, targetType: 1, targetId: 11 }],
      batchReports: [],
      userTaskProgress: [
        { userId: 1, taskCode: 'DAILY_CHECK_IN', periodKey: '2026-03-01' },
        { userId: 2, taskCode: 'WEEKLY_COMMENTER', periodKey: '2026-W10' }
      ]
    },
    seed: 'domain-integrity'
  })

  assert.ok(dataset.moderationActions.every((action) => action.reportRef == null))

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
  assert.equal(hotStartTargets.im_conversations, 1)
  assert.equal(hotStartTargets.user_task_progress, 122)
  assert.equal(imBusyTargets.im_conversations, 1)
  assert.equal(imBusyTargets.im_room_members, 32)
})
