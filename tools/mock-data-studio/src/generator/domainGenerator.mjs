import { createSeededRandom } from './random.mjs'

const REPORT_TARGET_TYPE_POST = 1
const REPORT_TARGET_TYPE_COMMENT = 2
const REPORT_TARGET_TYPE_USER = 3

const REPORT_REASONS = ['spam', 'abuse', 'spoiler', 'off_topic']
const MODERATION_ACTIONS = ['WARN', 'HIDE', 'DELETE', 'MUTE']
const TASK_CODES = ['DAILY_CHECK_IN', 'DAILY_POST', 'WEEKLY_COMMENTER', 'LIFETIME_RECEIVE_LIKE']
const TASK_STATUS_SEQUENCE = ['IN_PROGRESS', 'CLAIMABLE', 'CLAIMED']
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
    reports: readPhaseDeficit(plan, 'moderation', 'reports'),
    moderationActions: readPhaseDeficit(plan, 'moderation', 'moderation_actions'),
    userTaskProgress: readPhaseDeficit(plan, 'growth', 'user_task_progress')
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
    userTaskProgress: (existing.userTaskProgress ?? []).map((progress) => ({
      userId: normalizeId(progress.userId ?? progress.user_id),
      taskCode: String(progress.taskCode ?? progress.task_code ?? '').trim(),
      periodKey: String(progress.periodKey ?? progress.period_key ?? '').trim()
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

function describeReport(index) {
  return `治理样例 ${index + 1}：该内容需要版主进一步确认。`
}

function countPositive(deficits) {
  return Object.values(deficits).reduce((sum, count) => sum + count, 0)
}

function selectDistinctUsers(random, users, count) {
  return shuffleCopy(random, users).slice(0, Math.min(count, users.length))
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
      reports: [],
      moderationActions: [],
      userTaskProgress: []
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
      return {
        userId: entry.userId,
        taskCode: entry.taskCode,
        periodKey: entry.periodKey,
        currentValue: status === 'IN_PROGRESS' ? Math.max(0, targetValue - 1) : targetValue,
        targetValue,
        status,
        reachedAt,
        claimedAt,
        rewardGrantId: null,
        lastSourceEventId: `task-source:${plan?.batchId ?? 'batch'}:${index + 1}`
      }
    })

  return {
    seed: resolvedSeed,
    reports,
    moderationActions,
    userTaskProgress
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
