export const autoFillPhaseNames = ['community', 'im', 'growth', 'moderation', 'reward']
export const defaultAutoFillSceneKey = 'tech-community-hot-start'

function normalizeCount(value) {
  return Math.max(0, Number.parseInt(value, 10) || 0)
}

function createTarget({ entityType, targetCount, sceneKey, phase }) {
  return {
    entityType,
    targetKey: `${sceneKey}.${entityType}`,
    targetCount: normalizeCount(targetCount),
    payloadJson: {
      phase,
      sceneKey
    }
  }
}

function capAtFeasibleMaximum(targetCount, maximum) {
  if (!Number.isFinite(maximum)) {
    return normalizeCount(targetCount)
  }

  return Math.min(normalizeCount(targetCount), Math.max(0, Math.floor(maximum)))
}

function buildConversationCapacity(userCount) {
  if (userCount < 2) {
    return 0
  }

  return (userCount * (userCount - 1)) / 2
}

function buildTaskProgressCapacity(userCount) {
  return userCount * 61
}

function buildHotStartPhase2Targets(sceneKey, normalizedDefaults) {
  const conversationCapacity = buildConversationCapacity(normalizedDefaults.users)
  const imRoomCount =
    normalizedDefaults.users < 2 ? 0 : Math.max(Math.round(normalizedDefaults.users * 0.1), 10)

  return [
    createTarget({
      entityType: 'messages',
      targetCount: Math.max(Math.round(normalizedDefaults.users * 1.2), 120),
      sceneKey,
      phase: 'community'
    }),
    createTarget({
      entityType: 'notices',
      targetCount: Math.max(Math.round(normalizedDefaults.users * 0.6), 60),
      sceneKey,
      phase: 'community'
    }),
    createTarget({
      entityType: 'im_rooms',
      targetCount: imRoomCount,
      sceneKey,
      phase: 'im'
    }),
    createTarget({
      entityType: 'im_room_members',
      targetCount: imRoomCount * Math.min(Math.max(normalizedDefaults.users, 0), 3),
      sceneKey,
      phase: 'im'
    }),
    createTarget({
      entityType: 'im_room_messages',
      targetCount: Math.max(Math.round(normalizedDefaults.users * 1.2), 120),
      sceneKey,
      phase: 'im'
    }),
    createTarget({
      entityType: 'im_conversations',
      targetCount: capAtFeasibleMaximum(Math.max(Math.round(normalizedDefaults.users / 2), 50), conversationCapacity),
      sceneKey,
      phase: 'im'
    }),
    createTarget({
      entityType: 'im_private_messages',
      targetCount: Math.max(Math.round(normalizedDefaults.comments / 14), 180),
      sceneKey,
      phase: 'im'
    }),
    createTarget({
      entityType: 'growth_check_ins',
      targetCount: capAtFeasibleMaximum(Math.max(normalizedDefaults.users, 100), normalizedDefaults.users * 28),
      sceneKey,
      phase: 'growth'
    }),
    createTarget({
      entityType: 'user_task_progress',
      targetCount: capAtFeasibleMaximum(Math.max(normalizedDefaults.users * 2, 200), buildTaskProgressCapacity(normalizedDefaults.users)),
      sceneKey,
      phase: 'growth'
    }),
    createTarget({
      entityType: 'reward_accounts',
      targetCount: capAtFeasibleMaximum(Math.max(Math.round(normalizedDefaults.users * 0.6), 60), normalizedDefaults.users),
      sceneKey,
      phase: 'growth'
    }),
    createTarget({
      entityType: 'reward_ledgers',
      targetCount: Math.max(Math.round(normalizedDefaults.posts * 0.15), 120),
      sceneKey,
      phase: 'growth'
    }),
    createTarget({
      entityType: 'reward_grant_records',
      targetCount: Math.max(Math.round(normalizedDefaults.users * 0.66), 66),
      sceneKey,
      phase: 'growth'
    }),
    createTarget({
      entityType: 'reports',
      targetCount: Math.max(Math.round(normalizedDefaults.posts / 20), 40),
      sceneKey,
      phase: 'moderation'
    }),
    createTarget({
      entityType: 'moderation_actions',
      targetCount: Math.max(Math.round(normalizedDefaults.posts / 40), 20),
      sceneKey,
      phase: 'moderation'
    }),
    createTarget({
      entityType: 'reward_items',
      targetCount: 6,
      sceneKey,
      phase: 'reward'
    }),
    createTarget({
      entityType: 'reward_orders',
      targetCount: Math.max(Math.round(normalizedDefaults.users * 0.3), 30),
      sceneKey,
      phase: 'reward'
    })
  ]
}

export function buildScenePresets({ defaults } = {}) {
  const normalizedDefaults = {
    users: normalizeCount(defaults?.users ?? 100),
    posts: normalizeCount(defaults?.posts ?? 800),
    comments: normalizeCount(defaults?.comments ?? 2500)
  }
  const conversationCapacity = buildConversationCapacity(normalizedDefaults.users)
  const imBusyRoomCount =
    normalizedDefaults.users < 2 ? 0 : Math.max(Math.round(normalizedDefaults.users / 6), 16)

  return {
    'tech-community-hot-start': {
      sceneKey: 'tech-community-hot-start',
      targets: [
        createTarget({
          entityType: 'users',
          targetCount: normalizedDefaults.users,
          sceneKey: 'tech-community-hot-start',
          phase: 'community'
        }),
        createTarget({
          entityType: 'posts',
          targetCount: normalizedDefaults.posts,
          sceneKey: 'tech-community-hot-start',
          phase: 'community'
        }),
        createTarget({
          entityType: 'comments',
          targetCount: normalizedDefaults.comments,
          sceneKey: 'tech-community-hot-start',
          phase: 'community'
        }),
        ...buildHotStartPhase2Targets('tech-community-hot-start', normalizedDefaults)
      ]
    },
    'moderation-pressure': {
      sceneKey: 'moderation-pressure',
      targets: [
        createTarget({
          entityType: 'users',
          targetCount: normalizedDefaults.users,
          sceneKey: 'moderation-pressure',
          phase: 'community'
        }),
        createTarget({
          entityType: 'posts',
          targetCount: Math.max(normalizedDefaults.posts, 400),
          sceneKey: 'moderation-pressure',
          phase: 'community'
        }),
        createTarget({
          entityType: 'comments',
          targetCount: Math.max(normalizedDefaults.comments, 3200),
          sceneKey: 'moderation-pressure',
          phase: 'community'
        }),
        createTarget({
          entityType: 'notices',
          targetCount: Math.max(Math.round(normalizedDefaults.users * 0.8), 80),
          sceneKey: 'moderation-pressure',
          phase: 'community'
        }),
        createTarget({
          entityType: 'reports',
          targetCount: Math.max(Math.round(normalizedDefaults.posts / 4), 120),
          sceneKey: 'moderation-pressure',
          phase: 'moderation'
        }),
        createTarget({
          entityType: 'moderation_actions',
          targetCount: Math.max(Math.round(normalizedDefaults.posts / 8), 60),
          sceneKey: 'moderation-pressure',
          phase: 'moderation'
        }),
        createTarget({
          entityType: 'messages',
          targetCount: Math.max(Math.round(normalizedDefaults.users * 1.5), 150),
          sceneKey: 'moderation-pressure',
          phase: 'community'
        })
      ]
    },
    'im-busy': {
      sceneKey: 'im-busy',
      targets: [
        createTarget({
          entityType: 'users',
          targetCount: normalizedDefaults.users,
          sceneKey: 'im-busy',
          phase: 'community'
        }),
        createTarget({
          entityType: 'im_conversations',
          targetCount: capAtFeasibleMaximum(Math.max(Math.round(normalizedDefaults.users / 2), 50), conversationCapacity),
          sceneKey: 'im-busy',
          phase: 'im'
        }),
        createTarget({
          entityType: 'im_private_messages',
          targetCount: Math.max(normalizedDefaults.comments, 1800),
          sceneKey: 'im-busy',
          phase: 'im'
        }),
        createTarget({
          entityType: 'im_rooms',
          targetCount: imBusyRoomCount,
          sceneKey: 'im-busy',
          phase: 'im'
        }),
        createTarget({
          entityType: 'im_room_members',
          targetCount: imBusyRoomCount * Math.min(Math.max(normalizedDefaults.users, 0), 4),
          sceneKey: 'im-busy',
          phase: 'im'
        }),
        createTarget({
          entityType: 'im_room_messages',
          targetCount: Math.max(Math.round(normalizedDefaults.comments * 0.5), 900),
          sceneKey: 'im-busy',
          phase: 'im'
        })
      ]
    },
    'reward-ops-busy': {
      sceneKey: 'reward-ops-busy',
      targets: [
        createTarget({
          entityType: 'users',
          targetCount: normalizedDefaults.users,
          sceneKey: 'reward-ops-busy',
          phase: 'community'
        }),
        createTarget({
          entityType: 'growth_check_ins',
          targetCount: capAtFeasibleMaximum(Math.max(normalizedDefaults.users, 120), normalizedDefaults.users * 28),
          sceneKey: 'reward-ops-busy',
          phase: 'growth'
        }),
        createTarget({
          entityType: 'reward_ledgers',
          targetCount: Math.max(normalizedDefaults.posts, 600),
          sceneKey: 'reward-ops-busy',
          phase: 'growth'
        }),
        createTarget({
          entityType: 'reward_grant_records',
          targetCount: Math.max(Math.round(normalizedDefaults.posts / 6), 120),
          sceneKey: 'reward-ops-busy',
          phase: 'growth'
        }),
        createTarget({
          entityType: 'reward_items',
          targetCount: 8,
          sceneKey: 'reward-ops-busy',
          phase: 'reward'
        }),
        createTarget({
          entityType: 'reward_orders',
          targetCount: Math.max(Math.round(normalizedDefaults.posts / 8), 75),
          sceneKey: 'reward-ops-busy',
          phase: 'reward'
        })
      ]
    }
  }
}

export function resolveScenePreset({ sceneKey = defaultAutoFillSceneKey, config } = {}) {
  const presets = buildScenePresets({
    defaults: config?.autoFill?.defaults
  })
  const preset = presets[sceneKey]

  if (!preset) {
    throw new Error(`Unknown auto-fill scene preset: ${sceneKey}`)
  }

  return preset
}
