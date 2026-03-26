import { createSeededRandom } from './random.mjs'

const REPORT_TARGET_TYPE_POST = 1
const REPORT_TARGET_TYPE_COMMENT = 2
const REPORT_TARGET_TYPE_USER = 3

const NOTICE_TOPICS = ['comment', 'like', 'follow', 'moderation']
const REPORT_REASONS = ['spam', 'abuse', 'spoiler', 'off_topic']
const MODERATION_ACTIONS = ['WARN', 'HIDE', 'DELETE', 'MUTE']
const TASK_CODES = ['DAILY_CHECK_IN', 'DAILY_POST', 'WEEKLY_COMMENTER', 'LIFETIME_RECEIVE_LIKE']
const TASK_STATUS_SEQUENCE = ['IN_PROGRESS', 'CLAIMABLE', 'CLAIMED']
const REWARD_ITEM_MODES = ['AUTO', 'MANUAL']
const REWARD_ITEM_STATUSES = ['ACTIVE', 'ACTIVE', 'INACTIVE']
const REWARD_ORDER_STATUSES = ['PENDING', 'FULFILLED', 'REFUNDED']
const DAILY_PERIOD_KEYS = Array.from({ length: 28 }, (_unused, index) => `2026-03-${String(index + 1).padStart(2, '0')}`)
const WEEKLY_PERIOD_KEYS = Array.from({ length: 4 }, (_unused, index) => `2026-W${String(index + 10).padStart(2, '0')}`)

function normalizeCount(value) {
  return Math.max(0, Number.parseInt(value, 10) || 0)
}

function normalizeId(value) {
  const id = Number(value)

  if (!Number.isInteger(id) || id <= 0) {
    throw new Error(`expected positive integer id, received ${value}`)
  }

  return id
}

function createRef(kind, id) {
  return {
    kind,
    id
  }
}

function buildSeed(scope, plan, deficits, seed) {
  if (seed != null) {
    return seed
  }

  return [
    'phase-2',
    scope,
    plan?.sceneKey ?? 'scene',
    plan?.batchId ?? 'batch',
    ...Object.entries(deficits)
      .sort(([left], [right]) => left.localeCompare(right))
      .flatMap(([entityType, count]) => [entityType, count])
  ].join(':')
}

function readPhaseDeficit(plan, phaseName, entityType) {
  const phase = plan?.phases?.find((candidate) => candidate.name === phaseName)
  return normalizeCount(phase?.deficits?.[entityType] ?? plan?.deficits?.[entityType] ?? 0)
}

function buildDomainDeficits(plan) {
  return {
    messages: readPhaseDeficit(plan, 'community', 'messages'),
    notices: readPhaseDeficit(plan, 'community', 'notices'),
    reports: readPhaseDeficit(plan, 'moderation', 'reports'),
    moderationActions: readPhaseDeficit(plan, 'moderation', 'moderation_actions'),
    growthCheckIns: readPhaseDeficit(plan, 'growth', 'growth_check_ins'),
    userTaskProgress: readPhaseDeficit(plan, 'growth', 'user_task_progress'),
    rewardAccounts: readPhaseDeficit(plan, 'growth', 'reward_accounts'),
    rewardLedgers: readPhaseDeficit(plan, 'growth', 'reward_ledgers'),
    rewardGrantRecords: readPhaseDeficit(plan, 'growth', 'reward_grant_records'),
    rewardItems: readPhaseDeficit(plan, 'reward', 'reward_items'),
    rewardOrders: readPhaseDeficit(plan, 'reward', 'reward_orders')
  }
}

function buildImDeficits(plan) {
  return {
    imRooms: readPhaseDeficit(plan, 'im', 'im_rooms'),
    imRoomMembers: readPhaseDeficit(plan, 'im', 'im_room_members'),
    imRoomMessages: readPhaseDeficit(plan, 'im', 'im_room_messages'),
    imConversations: readPhaseDeficit(plan, 'im', 'im_conversations'),
    imPrivateMessages: readPhaseDeficit(plan, 'im', 'im_private_messages')
  }
}

function normalizeDomainExisting(existing = {}) {
  return {
    users: (existing.users ?? []).map((user) => ({ id: normalizeId(user.id) })),
    posts: (existing.posts ?? []).map((post) => ({ id: normalizeId(post.id) })),
    comments: (existing.comments ?? []).map((comment) => ({
      id: normalizeId(comment.id),
      postId: normalizeId(comment.postId ?? comment.post_id ?? 1),
      userId: normalizeId(comment.userId ?? comment.user_id ?? 1)
    })),
    reports: (existing.reports ?? []).map((report) => ({
      id: normalizeId(report.id),
      reporterId: normalizeId(report.reporterId ?? report.reporter_id),
      targetType: normalizeId(report.targetType ?? report.target_type),
      targetId: normalizeId(report.targetId ?? report.target_id)
    })),
    batchReports: (existing.batchReports ?? existing.reports ?? []).map((report) => ({
      id: normalizeId(report.id)
    })),
    growthCheckIns: (existing.growthCheckIns ?? []).map((entry) => ({
      userId: normalizeId(entry.userId ?? entry.user_id),
      bizDate: String(entry.bizDate ?? entry.biz_date ?? '').trim()
    })),
    userTaskProgress: (existing.userTaskProgress ?? []).map((progress) => ({
      userId: normalizeId(progress.userId ?? progress.user_id),
      taskCode: String(progress.taskCode ?? progress.task_code ?? '').trim(),
      periodKey: String(progress.periodKey ?? progress.period_key ?? '').trim()
    })),
    rewardAccounts: (existing.rewardAccounts ?? []).map((account) => ({
      userId: normalizeId(account.userId ?? account.user_id)
    })),
    rewardItems: (existing.rewardItems ?? []).map((item) => ({
      id: normalizeId(item.id)
    })),
    batchRewardItems: (existing.batchRewardItems ?? existing.rewardItems ?? []).map((item) => ({
      id: normalizeId(item.id)
    }))
  }
}

function normalizeImExisting(existing = {}) {
  return {
    users: (existing.users ?? []).map((user) => ({ id: normalizeId(user.id) })),
    conversations: (existing.conversations ?? []).map((conversation) => ({
      conversationId: String(conversation.conversationId ?? conversation.conversation_id ?? '').trim()
    }))
  }
}

function rotate(items, index) {
  return items[index % items.length]
}

function shuffleCopy(random, items) {
  return random.shuffle([...items])
}

function conversationIdForUsers(userA, userB) {
  const left = Math.min(userA, userB)
  const right = Math.max(userA, userB)
  return `${left}_${right}`
}

function describePrivateMessage(index) {
  return `第 ${index + 1} 条社区私信样例，方便演示会话预览与已读状态。`
}

function describeNotice(topic, index) {
  return JSON.stringify({
    topic,
    actorUserId: (index % 7) + 1,
    entityType: topic === 'follow' ? 'user' : 'post',
    entityId: index + 1,
    summary: `mock-data-studio ${topic} notice ${index + 1}`
  })
}

function describeReport(index) {
  return `治理样例 ${index + 1}：该内容需要版主进一步确认。`
}

function describeRewardItem(index) {
  return `演示兑换商品 ${index + 1}`
}

function countPositive(deficits) {
  return Object.values(deficits).reduce((sum, count) => sum + count, 0)
}

function selectDistinctUsers(random, users, count) {
  return shuffleCopy(random, users).slice(0, Math.min(count, users.length))
}

function rewardPeriodKey(taskCode, batchId, index) {
  if (taskCode === 'DAILY_CHECK_IN' || taskCode === 'DAILY_POST') {
    return `2026-03-${String(((batchId ?? 1) + index) % 28 + 1).padStart(2, '0')}`
  }

  if (taskCode === 'WEEKLY_COMMENTER') {
    return `2026-W${String((index % 4) + 10).padStart(2, '0')}`
  }

  return 'LIFETIME'
}

function taskTargetValue(taskCode) {
  if (taskCode === 'DAILY_CHECK_IN' || taskCode === 'DAILY_POST') {
    return 1
  }

  if (taskCode === 'WEEKLY_COMMENTER') {
    return 2
  }

  return 3
}

function buildGrowthCheckInKey(entry) {
  return `${entry.userId}:${entry.bizDate}`
}

function buildUserTaskProgressKey(entry) {
  return `${entry.userId}:${entry.taskCode}:${entry.periodKey}`
}

function periodKeysForTask(taskCode) {
  if (taskCode === 'DAILY_CHECK_IN' || taskCode === 'DAILY_POST') {
    return DAILY_PERIOD_KEYS
  }

  if (taskCode === 'WEEKLY_COMMENTER') {
    return WEEKLY_PERIOD_KEYS
  }

  return ['LIFETIME']
}

function ensureAvailableUsers(users, label) {
  if (users.length === 0) {
    throw new Error(`${label} requires at least one available user`)
  }
}

export function generateDomainPhaseDataset({ plan, existing = {}, seed } = {}) {
  const deficits = buildDomainDeficits(plan)
  const normalizedExisting = normalizeDomainExisting(existing)
  const resolvedSeed = buildSeed('community-domain', plan, deficits, seed)
  const random = createSeededRandom(resolvedSeed)

  if (countPositive(deficits) === 0) {
    return {
      seed: resolvedSeed,
      messages: [],
      notices: [],
      reports: [],
      moderationActions: [],
      growthCheckIns: [],
      userTaskProgress: [],
      rewardAccounts: [],
      rewardLedgers: [],
      rewardGrantRecords: [],
      rewardItems: [],
      rewardOrders: []
    }
  }

  ensureAvailableUsers(normalizedExisting.users, 'phase 2 generation')

  const userIds = normalizedExisting.users.map((user) => user.id)
  const shuffledUserIds = shuffleCopy(random, userIds)
  const reportTargets = [
    ...normalizedExisting.posts.map((post) => ({ targetType: REPORT_TARGET_TYPE_POST, targetId: post.id })),
    ...normalizedExisting.comments.map((comment) => ({ targetType: REPORT_TARGET_TYPE_COMMENT, targetId: comment.id })),
    ...normalizedExisting.users.map((user) => ({ targetType: REPORT_TARGET_TYPE_USER, targetId: user.id }))
  ]
  const seenReportKeys = new Set(
    normalizedExisting.reports.map(
      (report) => `${report.reporterId}:${report.targetType}:${report.targetId}`
    )
  )

  const messages = Array.from({ length: deficits.messages }, (_, index) => {
    const fromUserId = rotate(shuffledUserIds, index)
    const toUserId = rotate(shuffledUserIds, index + 1)
    const resolvedToUserId =
      fromUserId === toUserId && shuffledUserIds.length > 1 ? rotate(shuffledUserIds, index + 2) : toUserId

    return {
      fromUserId,
      toUserId: resolvedToUserId,
      conversationId: conversationIdForUsers(fromUserId, resolvedToUserId),
      content: describePrivateMessage(index),
      status: index % 4 === 0 ? 1 : 0
    }
  })

  const notices = Array.from({ length: deficits.notices }, (_, index) => {
    const topic = rotate(NOTICE_TOPICS, index)

    return {
      fromUserId: 0,
      toUserId: rotate(shuffledUserIds, index),
      conversationId: topic,
      content: describeNotice(topic, index),
      status: index % 3 === 0 ? 1 : 0
    }
  })

  const reports = []
  if (deficits.reports > 0) {
    if (reportTargets.length === 0) {
      throw new Error('report generation requires at least one post, comment, or user target')
    }

    let attempts = 0
    while (reports.length < deficits.reports && attempts < deficits.reports * 20) {
      attempts += 1
      const reporterId = rotate(shuffledUserIds, attempts - 1)
      const target = rotate(reportTargets, attempts - 1 + random.integer(0, Math.max(reportTargets.length - 1, 0)))

      if (target.targetType === REPORT_TARGET_TYPE_USER && target.targetId === reporterId) {
        continue
      }

      const reportKey = `${reporterId}:${target.targetType}:${target.targetId}`
      if (seenReportKeys.has(reportKey)) {
        continue
      }

      seenReportKeys.add(reportKey)
      reports.push({
        reporterId,
        targetType: target.targetType,
        targetId: target.targetId,
        reason: rotate(REPORT_REASONS, reports.length),
        detail: describeReport(reports.length),
        status: reports.length % 3 === 0 ? 0 : 1
      })
    }
  }

  const existingReportRefs = normalizedExisting.batchReports.map((report) => createRef('existing', report.id))
  const generatedReportRefs = reports.map((_report, index) => createRef('generated', index))
  const moderationActionReportRefs = [...generatedReportRefs, ...existingReportRefs]

  const moderationActions = Array.from({ length: deficits.moderationActions }, (_, index) => ({
    reportRef: moderationActionReportRefs.length === 0 ? null : rotate(moderationActionReportRefs, index),
    actorId: rotate(shuffledUserIds, index),
    action: rotate(MODERATION_ACTIONS, index),
    reason: `治理动作 ${index + 1}：用于演示后台 action 列表。`,
    durationSeconds: rotate([0, 3600, 21600, 86400], index)
  }))

  const seenGrowthCheckInKeys = new Set(
    normalizedExisting.growthCheckIns.map((entry) => buildGrowthCheckInKey(entry))
  )
  const growthCheckInCandidates = shuffleCopy(
    random,
    shuffledUserIds.flatMap((userId) =>
      DAILY_PERIOD_KEYS.map((bizDate, index) => ({
        userId,
        bizDate,
        streakCount: (index % 7) + 1
      }))
    )
  )
  const growthCheckIns = growthCheckInCandidates
    .filter((entry) => {
      const key = buildGrowthCheckInKey(entry)
      if (seenGrowthCheckInKeys.has(key)) {
        return false
      }

      seenGrowthCheckInKeys.add(key)
      return true
    })
    .slice(0, deficits.growthCheckIns)

  const rewardAccountUsers = selectDistinctUsers(
    random,
    normalizedExisting.users.filter(
      (user) => !normalizedExisting.rewardAccounts.some((account) => account.userId === user.id)
    ),
    deficits.rewardAccounts
  ).map((user) => user.id)

  const rewardLedgerBalanceByUser = new Map(rewardAccountUsers.map((userId) => [userId, 0]))
  const rewardLedgers = Array.from({ length: deficits.rewardLedgers }, (_, index) => {
    const userId = rewardAccountUsers.length > 0 ? rotate(rewardAccountUsers, index) : rotate(shuffledUserIds, index)
    const delta = 2 + (index % 5)
    const balanceAfter = (rewardLedgerBalanceByUser.get(userId) ?? 0) + delta

    rewardLedgerBalanceByUser.set(userId, balanceAfter)

    return {
      userId,
      eventId: `reward-ledger:${plan?.batchId ?? 'batch'}:${index + 1}`,
      eventType: index % 2 === 0 ? 'RewardGranted' : 'DailyCheckIn',
      delta,
      balanceAfter,
      frozenBalanceAfter: 0,
      bizKey: `reward-biz:${plan?.batchId ?? 'batch'}:${index + 1}`,
      sourceModule: 'mock-data-studio',
      remark: `奖励流水样例 ${index + 1}`
    }
  })

  const rewardGrantRecords = Array.from({ length: deficits.rewardGrantRecords }, (_, index) => {
    const userId = rewardAccountUsers.length > 0 ? rotate(rewardAccountUsers, index) : rotate(shuffledUserIds, index)
    const sourceLedger = rewardLedgers.length === 0 ? null : rotate(rewardLedgers, index)

    return {
      grantId: `grant:${plan?.batchId ?? 'batch'}:${index + 1}`,
      userId,
      grantType: rotate(['CHECK_IN', 'TASK', 'ADMIN_ADJUSTMENT'], index),
      sourceEventId: sourceLedger?.eventId ?? `grant-source:${plan?.batchId ?? 'batch'}:${index + 1}`,
      sourceEventType: sourceLedger?.eventType ?? 'TaskCompleted',
      growthDelta: 1 + (index % 4),
      rewardDelta: 2 + (index % 3),
      status: 'SUCCEEDED'
    }
  })

  const rewardAccounts = rewardAccountUsers.map((userId) => ({
    userId,
    availableBalance: rewardLedgerBalanceByUser.get(userId) ?? 0,
    frozenBalance: 0,
    version: rewardLedgers.filter((ledger) => ledger.userId === userId).length
  }))

  const seenUserTaskProgressKeys = new Set(
    normalizedExisting.userTaskProgress.map((entry) => buildUserTaskProgressKey(entry))
  )
  const userTaskProgressCandidates = shuffleCopy(
    random,
    shuffledUserIds.flatMap((userId) =>
      TASK_CODES.flatMap((taskCode) =>
        periodKeysForTask(taskCode).map((periodKey) => ({
          userId,
          taskCode,
          periodKey
        }))
      )
    )
  )
  const userTaskProgress = userTaskProgressCandidates
    .filter((entry) => {
      const key = buildUserTaskProgressKey(entry)
      if (seenUserTaskProgressKeys.has(key)) {
        return false
      }

      seenUserTaskProgressKeys.add(key)
      return true
    })
    .slice(0, deficits.userTaskProgress)
    .map((entry, index) => {
      const status = rotate(TASK_STATUS_SEQUENCE, index)
      const targetValue = taskTargetValue(entry.taskCode)
      const reachedAt = status === 'IN_PROGRESS' ? null : `2026-03-${String((index % 28) + 1).padStart(2, '0')}T08:00:00.000Z`
      const claimedAt = status === 'CLAIMED' ? `2026-03-${String((index % 28) + 1).padStart(2, '0')}T09:00:00.000Z` : null
      const rewardGrant = status === 'CLAIMED' && rewardGrantRecords.length > 0 ? rotate(rewardGrantRecords, index) : null

      return {
        userId: entry.userId,
        taskCode: entry.taskCode,
        periodKey: entry.periodKey,
        currentValue: status === 'IN_PROGRESS' ? Math.max(0, targetValue - 1) : targetValue,
        targetValue,
        status,
        reachedAt,
        claimedAt,
        rewardGrantId: rewardGrant?.grantId ?? null,
        lastSourceEventId: `task-source:${plan?.batchId ?? 'batch'}:${index + 1}`
      }
    })

  const rewardItems = Array.from({ length: deficits.rewardItems }, (_, index) => ({
    itemName: describeRewardItem(index),
    itemDesc: `批次 ${plan?.batchId ?? 'batch'} 的演示奖励商品 ${index + 1}`,
    costBalance: 6 + index * 2,
    stock: 10 + index * 5,
    perUserLimit: index % 2 === 0 ? 1 : 2,
    fulfillmentMode: rotate(REWARD_ITEM_MODES, index),
    status: rotate(REWARD_ITEM_STATUSES, index)
  }))

  const rewardItemRefs = [
    ...normalizedExisting.batchRewardItems.map((item) => createRef('existing', item.id)),
    ...rewardItems.map((_item, index) => createRef('generated', index))
  ]
  const rewardOrders =
    rewardItemRefs.length === 0
      ? []
      : Array.from({ length: deficits.rewardOrders }, (_, index) => {
          const itemRef = rotate(rewardItemRefs, index)
          const generatedItem = itemRef?.kind === 'generated' ? rewardItems[itemRef.id] : null

          return {
            redeemRequestId: `redeem:${plan?.batchId ?? 'batch'}:${index + 1}`,
            userId: rewardAccountUsers.length > 0 ? rotate(rewardAccountUsers, index) : rotate(shuffledUserIds, index),
            itemRef,
            status: rotate(REWARD_ORDER_STATUSES, index),
            costBalanceSnapshot: generatedItem?.costBalance ?? 10 + index,
            fulfillmentModeSnapshot: generatedItem?.fulfillmentMode ?? 'MANUAL',
            itemNameSnapshot: generatedItem?.itemName ?? `既有商品 ${index + 1}`,
            itemDescSnapshot: generatedItem?.itemDesc ?? `历史奖励商品 ${index + 1}`
          }
        })

  return {
    seed: resolvedSeed,
    messages,
    notices,
    reports,
    moderationActions,
    growthCheckIns,
    userTaskProgress,
    rewardAccounts,
    rewardLedgers,
    rewardGrantRecords,
    rewardItems,
    rewardOrders
  }
}

function pickConversationPairs(random, users, count, existingConversationIds) {
  const shuffledUsers = shuffleCopy(random, users)
  const pairs = []
  const seenConversationIds = new Set(existingConversationIds)

  for (let leftIndex = 0; leftIndex < shuffledUsers.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < shuffledUsers.length; rightIndex += 1) {
      const conversationId = conversationIdForUsers(shuffledUsers[leftIndex].id, shuffledUsers[rightIndex].id)
      if (seenConversationIds.has(conversationId)) {
        continue
      }

      seenConversationIds.add(conversationId)
      pairs.push({
        conversationId,
        userA: Math.min(shuffledUsers[leftIndex].id, shuffledUsers[rightIndex].id),
        userB: Math.max(shuffledUsers[leftIndex].id, shuffledUsers[rightIndex].id)
      })
    }
  }

  return pairs.slice(0, count)
}

function distributeCounts(totalCount, bucketCount, minimumPerBucket = 0) {
  if (bucketCount === 0) {
    return []
  }

  const counts = Array.from({ length: bucketCount }, () => minimumPerBucket)
  let remaining = Math.max(0, totalCount - bucketCount * minimumPerBucket)
  let index = 0

  while (remaining > 0) {
    counts[index % bucketCount] += 1
    remaining -= 1
    index += 1
  }

  return counts
}

export function generateImPhaseDataset({ plan, existing = {}, seed } = {}) {
  const deficits = buildImDeficits(plan)
  const normalizedExisting = normalizeImExisting(existing)
  const resolvedSeed = buildSeed('im', plan, deficits, seed)
  const random = createSeededRandom(resolvedSeed)

  if (countPositive(deficits) === 0) {
    return {
      seed: resolvedSeed,
      rooms: [],
      roomMembers: [],
      roomMessages: [],
      conversations: [],
      privateMessages: []
    }
  }

  ensureAvailableUsers(normalizedExisting.users, 'IM generation')

  const shuffledUsers = shuffleCopy(random, normalizedExisting.users)
  if (shuffledUsers.length < 2 && (deficits.imRooms > 0 || deficits.imConversations > 0)) {
    throw new Error('IM generation requires at least two available users')
  }

  const roomIds = Array.from({ length: deficits.imRooms }, (_, index) => (plan?.batchId ?? 1) * 1000000 + index + 1)
  const roomMemberCounts = distributeCounts(
    deficits.imRoomMembers,
    roomIds.length,
    roomIds.length > 0 ? Math.min(2, deficits.imRoomMembers >= roomIds.length * 2 ? 2 : 1) : 0
  )
  const roomMessageCounts = distributeCounts(deficits.imRoomMessages, roomIds.length, 0)

  const roomMembers = []
  const roomMessages = []
  const rooms = roomIds.map((roomId, roomIndex) => {
    const memberCount = Math.min(roomMemberCounts[roomIndex] ?? 0, shuffledUsers.length)
    const members = selectDistinctUsers(random, shuffledUsers, memberCount).map((user, memberIndex) => {
      return {
        roomId,
        userId: user.id,
        role: memberIndex === 0 ? 1 : 0
      }
    })

    roomMembers.push(...members)

    const messageCount = members.length === 0 ? 0 : roomMessageCounts[roomIndex] ?? 0
    const roomMessageRows = Array.from({ length: messageCount }, (_unused, messageIndex) => {
      const author = rotate(members, messageIndex)
      return {
        roomId,
        seq: messageIndex + 1,
        messageId: (plan?.batchId ?? 1) * 10000000 + roomMessages.length + messageIndex + 1,
        fromUserId: author.userId,
        content: `房间 ${roomId} 的第 ${messageIndex + 1} 条消息样例。`,
        clientMsgId: `room-${roomId}-client-${messageIndex + 1}`
      }
    })

    roomMessages.push(...roomMessageRows)

    return {
      roomId,
      name: `Demo Room ${roomIndex + 1}`,
      lastSeq: roomMessageRows.length
    }
  })

  const conversationPairs = pickConversationPairs(
    random,
    shuffledUsers,
    deficits.imConversations,
    normalizedExisting.conversations.map((conversation) => conversation.conversationId)
  )
  const privateMessageCounts = distributeCounts(deficits.imPrivateMessages, conversationPairs.length, 0)
  const privateMessages = []

  const conversations = conversationPairs.map((conversation, conversationIndex) => {
    const messageCount = privateMessageCounts[conversationIndex] ?? 0
    const conversationMessages = Array.from({ length: messageCount }, (_unused, messageIndex) => {
      const fromUserId = messageIndex % 2 === 0 ? conversation.userA : conversation.userB
      const toUserId = fromUserId === conversation.userA ? conversation.userB : conversation.userA

      return {
        conversationId: conversation.conversationId,
        seq: messageIndex + 1,
        messageId: (plan?.batchId ?? 1) * 20000000 + privateMessages.length + messageIndex + 1,
        fromUserId,
        toUserId,
        content: `会话 ${conversation.conversationId} 的第 ${messageIndex + 1} 条私信样例。`,
        clientMsgId: `conversation-${conversation.conversationId}-client-${messageIndex + 1}`
      }
    })

    privateMessages.push(...conversationMessages)

    return {
      conversationId: conversation.conversationId,
      userA: conversation.userA,
      userB: conversation.userB,
      lastSeq: conversationMessages.length
    }
  })

  return {
    seed: resolvedSeed,
    rooms,
    roomMembers,
    roomMessages,
    conversations,
    privateMessages
  }
}
