import assert from 'node:assert/strict'
import test from 'node:test'

import { bufferToUuid } from '../src/db/uuidv7.mjs'
import { generateCommunityPhaseDataset } from '../src/generator/contentGenerator.mjs'
import { createCommunityWriter } from '../src/writers/communityWriter.mjs'
import { createDeleteBatchService } from '../src/writers/deleteBatchService.mjs'
import { createImWriter } from '../src/writers/imWriter.mjs'

function normalizeSql(sql) {
  return sql.replace(/;+$/u, '').trim().replace(/\s+/gu, ' ').toLowerCase()
}

function metadataId(sequence) {
  return `01965429-b34a-7000-8000-${String(sequence).padStart(12, '0')}`
}

function normalizeDbId(value) {
  return bufferToUuid(value)
}

class FakeCommunityDb {
  constructor(overrides = {}) {
    this.state = {
      nextUserId: 2,
      nextPostId: 2,
      nextCommentId: 2,
      nextReportId: 1,
      nextModerationActionId: 1,
      nextUserTaskProgressId: 1,
      imRooms: [],
      imRoomMembers: [],
      imRoomMessages: [],
      imConversations: [],
      imPrivateMessages: [],
      categories: [
        { id: 1, name: '公告' },
        { id: 2, name: '技术' },
        { id: 3, name: '兴趣' }
      ],
      users: [
        {
          id: 1,
          username: 'existing-user',
          email: 'existing@example.com'
        }
      ],
      posts: [
        {
          id: 1,
          user_id: 1,
          category_id: 2,
          title: 'Existing post',
          content: 'Already present',
          comment_count: 1,
          score: 42
        }
      ],
      comments: [
        {
          id: 1,
          user_id: 1,
          entity_type: 1,
          entity_id: 1,
          target_id: 0,
          content: 'Existing direct comment'
        }
      ],
      follows: [],
      likes: [],
      reports: [],
      moderationActions: [],
      userTaskProgress: [],
      ...structuredClone(overrides)
    }
  }

  async withTransaction(work) {
    return work(this)
  }

  async query(sql) {
    const normalized = normalizeSql(sql)

    if (normalized.includes(' from category ')) {
      return this.state.categories.map((category) => ({ ...category }))
    }

    if (normalized.includes(' from user ')) {
      return this.state.users.map((user) => ({
        id: user.id
      }))
    }

    if (normalized.includes(' from discuss_post ')) {
      return this.state.posts.map((post) => ({
        id: post.id,
        comment_count: post.comment_count
      }))
    }

    if (normalized.includes(' from comment ')) {
      return this.state.comments
        .filter((comment) => comment.entity_type === 1)
        .map((comment) => ({
          id: comment.id,
          post_id: comment.entity_id,
          user_id: comment.user_id
        }))
    }

    if (normalized.includes(' from social_follow ')) {
      return this.state.follows.map((follow) => ({ ...follow }))
    }

    if (normalized.includes(' from social_like ')) {
      return this.state.likes.map((like) => ({ ...like }))
    }

    if (normalized.includes(' from report ')) {
      return this.state.reports.map((report) => ({
        id: report.id,
        reporter_id: report.reporter_id,
        target_type: report.target_type,
        target_id: report.target_id
      }))
    }

    if (normalized.includes(' from user_task_progress ')) {
      return this.state.userTaskProgress.map((entry) => ({
        user_id: entry.user_id,
        task_code: entry.task_code,
        period_key: entry.period_key
      }))
    }

    if (normalized.includes(' from im_core.im_conversation ')) {
      return this.state.imConversations.map((conversation) => ({
        conversation_id: conversation.conversation_id,
        user_a: conversation.user_a,
        user_b: conversation.user_b
      }))
    }

    throw new Error(`Unsupported query: ${sql}`)
  }

  async execute(sql, params = []) {
    const normalized = normalizeSql(sql)

    if (normalized.startsWith('insert into user ')) {
      return this.#insertUsers(params)
    }

    if (normalized.startsWith('insert into discuss_post ')) {
      return this.#insertPosts(params)
    }

    if (normalized.startsWith('insert into comment ')) {
      return this.#insertComments(params)
    }

    if (normalized.startsWith('insert into social_follow ')) {
      return this.#insertFollows(params)
    }

    if (normalized.startsWith('insert into social_like ')) {
      return this.#insertLikes(params)
    }

    if (normalized.startsWith('insert into report ')) {
      return this.#insertReports(params)
    }

    if (normalized.startsWith('insert into moderation_action ')) {
      return this.#insertModerationActions(params)
    }

    if (normalized.startsWith('insert into user_task_progress ')) {
      return this.#insertUserTaskProgress(params)
    }

    if (normalized.startsWith('insert into im_core.im_room ')) {
      return this.#insertImRooms(params)
    }

    if (normalized.startsWith('insert into im_core.im_room_member ')) {
      return this.#insertImRoomMembers(params)
    }

    if (normalized.startsWith('insert into im_core.im_room_message ')) {
      return this.#insertImRoomMessages(params)
    }

    if (normalized.startsWith('insert into im_core.im_conversation ')) {
      return this.#insertImConversations(params)
    }

    if (normalized.startsWith('insert into im_core.im_private_message ')) {
      return this.#insertImPrivateMessages(params)
    }

    if (normalized.startsWith('update discuss_post set comment_count = ? where id = ?')) {
      const [commentCount, postId] = params
      const post = this.state.posts.find((candidate) => candidate.id === postId)
      post.comment_count = commentCount
      return { affectedRows: 1 }
    }

    throw new Error(`Unsupported execute: ${sql}`)
  }

  #insertUsers(params) {
    const columnCount = 8
    const firstInsertId = this.state.nextUserId

    for (let index = 0; index < params.length; index += columnCount) {
      const [
        username,
        password,
        salt,
        email,
        type,
        status,
        headerUrl,
        createTime
      ] = params.slice(index, index + columnCount)

      if (this.state.users.some((user) => user.username === username)) {
        throw new Error(`Duplicate username ${username}`)
      }

      if (this.state.users.some((user) => user.email === email)) {
        throw new Error(`Duplicate email ${email}`)
      }

      this.state.users.push({
        id: this.state.nextUserId++,
        username,
        password,
        salt,
        email,
        type,
        status,
        header_url: headerUrl,
        create_time: createTime
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: firstInsertId
    }
  }

  #insertPosts(params) {
    const columnCount = 9
    const firstInsertId = this.state.nextPostId

    for (let index = 0; index < params.length; index += columnCount) {
      const [userId, categoryId, title, content, type, status, createTime, commentCount, score] = params.slice(
        index,
        index + columnCount
      )

      this.state.posts.push({
        id: this.state.nextPostId++,
        user_id: userId,
        category_id: categoryId,
        title,
        content,
        type,
        status,
        create_time: createTime,
        comment_count: commentCount,
        score
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: firstInsertId
    }
  }

  #insertComments(params) {
    const columnCount = 7
    const firstInsertId = this.state.nextCommentId

    for (let index = 0; index < params.length; index += columnCount) {
      const [userId, entityType, entityId, targetId, content, status, createTime] = params.slice(
        index,
        index + columnCount
      )

      this.state.comments.push({
        id: this.state.nextCommentId++,
        user_id: userId,
        entity_type: entityType,
        entity_id: entityId,
        target_id: targetId,
        content,
        status,
        create_time: createTime
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: firstInsertId
    }
  }

  #insertFollows(params) {
    const columnCount = 4

    for (let index = 0; index < params.length; index += columnCount) {
      const [userId, entityType, entityId, createdAt] = params.slice(index, index + columnCount)
      if (
        this.state.follows.some(
          (follow) => follow.user_id === userId && follow.entity_type === entityType && follow.entity_id === entityId
        )
      ) {
        throw new Error(`Duplicate social_follow ${userId}:${entityType}:${entityId}`)
      }

      this.state.follows.push({
        user_id: userId,
        entity_type: entityType,
        entity_id: entityId,
        created_at: createdAt
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: 0
    }
  }

  #insertLikes(params) {
    const columnCount = 4

    for (let index = 0; index < params.length; index += columnCount) {
      const [userId, entityType, entityId, createdAt] = params.slice(index, index + columnCount)
      if (
        this.state.likes.some(
          (like) => like.user_id === userId && like.entity_type === entityType && like.entity_id === entityId
        )
      ) {
        throw new Error(`Duplicate social_like ${userId}:${entityType}:${entityId}`)
      }

      this.state.likes.push({
        user_id: userId,
        entity_type: entityType,
        entity_id: entityId,
        created_at: createdAt
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: 0
    }
  }

  #insertReports(params) {
    const columnCount = 7
    const firstInsertId = this.state.nextReportId

    for (let index = 0; index < params.length; index += columnCount) {
      const [reporterId, targetType, targetId, reason, detail, status, createTime] = params.slice(index, index + columnCount)
      this.state.reports.push({
        id: this.state.nextReportId++,
        reporter_id: reporterId,
        target_type: targetType,
        target_id: targetId,
        reason,
        detail,
        status,
        create_time: createTime
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: firstInsertId
    }
  }

  #insertModerationActions(params) {
    const columnCount = 6
    const firstInsertId = this.state.nextModerationActionId

    for (let index = 0; index < params.length; index += columnCount) {
      const [reportId, actorId, action, reason, durationSeconds, createTime] = params.slice(index, index + columnCount)
      this.state.moderationActions.push({
        id: this.state.nextModerationActionId++,
        report_id: reportId,
        actor_id: actorId,
        action,
        reason,
        duration_seconds: durationSeconds,
        create_time: createTime
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: firstInsertId
    }
  }

  #insertUserTaskProgress(params) {
    const columnCount = 11
    const firstInsertId = this.state.nextUserTaskProgressId

    for (let index = 0; index < params.length; index += columnCount) {
      const [
        userId,
        taskCode,
        periodKey,
        currentValue,
        targetValue,
        status,
        reachedAt,
        claimedAt,
        rewardGrantId,
        lastSourceEventId,
        updateTime
      ] = params.slice(index, index + columnCount)
      this.state.userTaskProgress.push({
        id: this.state.nextUserTaskProgressId++,
        user_id: userId,
        task_code: taskCode,
        period_key: periodKey,
        current_value: currentValue,
        target_value: targetValue,
        status,
        reached_at: reachedAt,
        claimed_at: claimedAt,
        reward_grant_id: rewardGrantId,
        last_source_event_id: lastSourceEventId,
        update_time: updateTime
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: firstInsertId
    }
  }

  #insertImRooms(params) {
    const columnCount = 5

    for (let index = 0; index < params.length; index += columnCount) {
      const [roomId, name, lastSeq, createdAt, updatedAt] = params.slice(index, index + columnCount)
      this.state.imRooms.push({
        room_id: roomId,
        name,
        last_seq: lastSeq,
        created_at: createdAt,
        updated_at: updatedAt
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: 0
    }
  }

  #insertImRoomMembers(params) {
    const columnCount = 4

    for (let index = 0; index < params.length; index += columnCount) {
      const [roomId, userId, role, joinedAt] = params.slice(index, index + columnCount)
      this.state.imRoomMembers.push({
        room_id: roomId,
        user_id: userId,
        role,
        joined_at: joinedAt
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: 0
    }
  }

  #insertImRoomMessages(params) {
    const columnCount = 7

    for (let index = 0; index < params.length; index += columnCount) {
      const [roomId, seq, messageId, fromUserId, content, clientMsgId, createdAt] = params.slice(index, index + columnCount)
      this.state.imRoomMessages.push({
        room_id: roomId,
        seq,
        message_id: messageId,
        from_user_id: fromUserId,
        content,
        client_msg_id: clientMsgId,
        created_at: createdAt
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: 0
    }
  }

  #insertImConversations(params) {
    const columnCount = 6

    for (let index = 0; index < params.length; index += columnCount) {
      const [conversationId, userA, userB, lastSeq, createdAt, updatedAt] = params.slice(index, index + columnCount)
      this.state.imConversations.push({
        conversation_id: conversationId,
        user_a: userA,
        user_b: userB,
        last_seq: lastSeq,
        created_at: createdAt,
        updated_at: updatedAt
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: 0
    }
  }

  #insertImPrivateMessages(params) {
    const columnCount = 8

    for (let index = 0; index < params.length; index += columnCount) {
      const [conversationId, seq, messageId, fromUserId, toUserId, content, clientMsgId, createdAt] = params.slice(
        index,
        index + columnCount
      )
      this.state.imPrivateMessages.push({
        conversation_id: conversationId,
        seq,
        message_id: messageId,
        from_user_id: fromUserId,
        to_user_id: toUserId,
        content,
        client_msg_id: clientMsgId,
        created_at: createdAt
      })
    }

    return {
      affectedRows: params.length / columnCount,
      insertId: 0
    }
  }
}

function createEntityRefRepositoryDouble() {
  const refsByBatch = new Map()
  const appendCalls = []

  return {
    appendCalls,
    async appendForBatch(batchId, refs) {
      appendCalls.push({
        batchId,
        refs: structuredClone(refs)
      })
      const existing = refsByBatch.get(batchId) ?? []
      const next = [...existing, ...structuredClone(refs)]
      refsByBatch.set(batchId, next)
      return structuredClone(next)
    },
    async listByBatchId(batchId) {
      return structuredClone(refsByBatch.get(batchId) ?? [])
    }
  }
}

function createPlan(overrides = {}) {
  const deficits = {
    users: 2,
    posts: 3,
    comments: 5,
    ...overrides.deficits
  }

  return {
    batchId: 42,
    sceneKey: 'tech-community-hot-start',
    deficits,
    phases: [
      {
        name: 'community',
        deficits,
        needsWork: true
      }
    ],
    ...overrides
  }
}

function createPhase2Plan(overrides = {}) {
  const communityDeficits = {
    users: 0,
    posts: 0,
    comments: 0,
    ...overrides.communityDeficits
  }
  const moderationDeficits = {
    reports: 2,
    moderation_actions: 2,
    ...overrides.moderationDeficits
  }
  const growthDeficits = {
    user_task_progress: 4,
    ...overrides.growthDeficits
  }

  return {
    batchId: 42,
    sceneKey: 'tech-community-hot-start',
    deficits: {
      ...communityDeficits,
      ...moderationDeficits,
      ...growthDeficits
    },
    phases: [
      {
        name: 'community',
        deficits: communityDeficits,
        needsWork: Object.values(communityDeficits).some((count) => count > 0)
      },
      {
        name: 'growth',
        deficits: growthDeficits,
        needsWork: Object.values(growthDeficits).some((count) => count > 0)
      },
      {
        name: 'moderation',
        deficits: moderationDeficits,
        needsWork: Object.values(moderationDeficits).some((count) => count > 0)
      }
    ],
    ...overrides
  }
}

function createImPlan(overrides = {}) {
  const imDeficits = {
    im_rooms: 2,
    im_room_members: 6,
    im_room_messages: 5,
    im_conversations: 3,
    im_private_messages: 7,
    ...overrides.imDeficits
  }

  return {
    batchId: 42,
    sceneKey: 'tech-community-hot-start',
    deficits: imDeficits,
    phases: [
      {
        name: 'im',
        deficits: imDeficits,
        needsWork: Object.values(imDeficits).some((count) => count > 0)
      }
    ],
    ...overrides
  }
}

function countByEntityType(refs) {
  return refs.reduce((counts, ref) => {
    counts[ref.entityType] = (counts[ref.entityType] ?? 0) + 1
    return counts
  }, {})
}

test('community writer records refs for each inserted row set and keeps visible aggregates in sync', async () => {
  const db = new FakeCommunityDb()
  const entityRefRepository = createEntityRefRepositoryDouble()
  const writer = createCommunityWriter({
    db,
    entityRefRepository,
    now: (() => {
      let tick = 0
      const start = Date.parse('2026-03-25T08:00:00.000Z')
      return () => new Date(start + tick++ * 1000).toISOString()
    })()
  })

  const result = await writer.writePhase({
    batchId: 42,
    plan: createPlan()
  })

  assert.deepEqual(result.insertedCounts, {
    users: 2,
    posts: 3,
    comments: 5,
    socialFollows: result.insertedCounts.socialFollows,
    socialLikes: result.insertedCounts.socialLikes,
    reports: 0,
    moderationActions: 0,
    userTaskProgress: 0
  })
  assert.ok(result.insertedCounts.socialFollows > 0)
  assert.ok(result.insertedCounts.socialLikes > 0)

  const recordedRefs = await entityRefRepository.listByBatchId(42)
  assert.deepEqual(countByEntityType(recordedRefs), {
    users: 2,
    posts: 3,
    comments: 5,
    social_follows: result.insertedCounts.socialFollows,
    social_likes: result.insertedCounts.socialLikes
  })
  assert.deepEqual(recordedRefs, result.generatedRefs)

  const insertedUsers = db.state.users.slice(-2)
  const insertedPosts = db.state.posts.slice(-3)
  const insertedComments = db.state.comments.slice(-5)

  assert.ok(insertedUsers.every((user) => !Object.hasOwn(user, 'score')))
  assert.deepEqual(
    recordedRefs.filter((ref) => ref.entityType === 'users').map((ref) => ref.entityKey),
    insertedUsers.map((user) => String(user.id))
  )
  assert.deepEqual(
    recordedRefs.filter((ref) => ref.entityType === 'posts').map((ref) => ref.entityKey),
    insertedPosts.map((post) => String(post.id))
  )
  assert.deepEqual(
    recordedRefs.filter((ref) => ref.entityType === 'comments').map((ref) => ref.entityKey),
    insertedComments.map((comment) => String(comment.id))
  )
  assert.ok(
    recordedRefs
      .filter((ref) => ref.entityType === 'social_follows')
      .every((ref) => /^\d+:3:\d+$/u.test(ref.entityKey))
  )
  assert.ok(
    recordedRefs
      .filter((ref) => ref.entityType === 'social_likes')
      .every((ref) => /^\d+:(1|2):\d+$/u.test(ref.entityKey))
  )

  for (const post of db.state.posts) {
    const actualCommentCount = db.state.comments.filter((comment) => {
      if (comment.entity_type === 1) {
        return comment.entity_id === post.id
      }

      const parentComment = db.state.comments.find((candidate) => candidate.id === comment.entity_id)
      return parentComment?.entity_type === 1 && parentComment.entity_id === post.id
    }).length

    assert.equal(post.comment_count, actualCommentCount)
  }
})

test('community writer inserts generated users as active accounts', async () => {
  const db = new FakeCommunityDb()
  const entityRefRepository = createEntityRefRepositoryDouble()
  const writer = createCommunityWriter({
    db,
    entityRefRepository,
    now: (() => {
      let tick = 0
      const start = Date.parse('2026-03-25T08:30:00.000Z')
      return () => new Date(start + tick++ * 1000).toISOString()
    })()
  })

  await writer.writePhase({
    batchId: 42,
    plan: createPlan()
  })

  const insertedUsers = db.state.users.slice(-2)
  assert.equal(insertedUsers.length, 2)
  assert.ok(insertedUsers.every((user) => user.status === 1))
})

test('community writer top-up avoids user identity and social graph collisions on incremental runs', async () => {
  const plan = createPlan({
    deficits: {
      users: 0,
      posts: 1,
      comments: 1
    }
  })
  const predicted = generateCommunityPhaseDataset({
    plan,
    existing: {
      users: [{ id: 1 }, { id: 2 }, { id: 3 }],
      posts: [{ id: 10, commentCount: 1 }],
      comments: [{ id: 20, postId: 10, userId: 1 }]
    }
  })
  const duplicatedFollow = predicted.follows[0]
  const duplicatedLike = predicted.likes.find(
    (like) => like.userRef.kind === 'existing' && like.entityRef.kind === 'existing'
  )

  assert.ok(duplicatedFollow)
  assert.ok(duplicatedLike)

  const db = new FakeCommunityDb({
    nextUserId: 4,
    nextPostId: 11,
    nextCommentId: 21,
    users: [
      {
        id: 1,
        username: 'existing-user-1',
        email: 'existing-1@example.com'
      },
      {
        id: 2,
        username: 'existing-user-2',
        email: 'existing-2@example.com'
      },
      {
        id: 3,
        username: 'existing-user-3',
        email: 'existing-3@example.com'
      }
    ],
    posts: [
      {
        id: 10,
        user_id: 1,
        category_id: 2,
        title: 'Existing post',
        content: 'Already present',
        comment_count: 1,
        score: 42
      }
    ],
    comments: [
      {
        id: 20,
        user_id: 1,
        entity_type: 1,
        entity_id: 10,
        target_id: 0,
        content: 'Existing direct comment'
      }
    ],
    follows: [
      {
        user_id: duplicatedFollow.followerUserRef.id,
        entity_type: 3,
        entity_id: duplicatedFollow.followedUserRef.id,
        created_at: '2026-03-25 07:59:00.000'
      }
    ],
    likes: [
      {
        user_id: duplicatedLike.userRef.id,
        entity_type: duplicatedLike.entityType === 'posts' ? 1 : 2,
        entity_id: duplicatedLike.entityRef.id,
        created_at: '2026-03-25 07:59:01.000'
      }
    ]
  })
  const entityRefRepository = createEntityRefRepositoryDouble()
  const writer = createCommunityWriter({
    db,
    entityRefRepository,
    now: (() => {
      let tick = 0
      const start = Date.parse('2026-03-25T09:00:00.000Z')
      return () => new Date(start + tick++ * 1000).toISOString()
    })()
  })

  const result = await writer.writePhase({
    batchId: 42,
    plan
  })

  assert.equal(result.insertedCounts.users, 0)
  assert.equal(result.insertedCounts.posts, 1)
  assert.equal(result.insertedCounts.comments, 1)
  assert.ok(result.insertedCounts.socialFollows <= predicted.follows.length)
  assert.ok(result.insertedCounts.socialLikes <= predicted.likes.length)
  assert.equal(
    db.state.follows.filter(
      (follow) => follow.user_id === duplicatedFollow.followerUserRef.id && follow.entity_id === duplicatedFollow.followedUserRef.id
    ).length,
    1
  )
  assert.equal(
    db.state.likes.filter(
      (like) =>
        like.user_id === duplicatedLike.userRef.id &&
        like.entity_type === (duplicatedLike.entityType === 'posts' ? 1 : 2) &&
        like.entity_id === duplicatedLike.entityRef.id
    ).length,
    1
  )
  assert.equal(
    new Set(db.state.follows.map((follow) => `${follow.user_id}:${follow.entity_type}:${follow.entity_id}`)).size,
    db.state.follows.length
  )
  assert.equal(
    new Set(db.state.likes.map((like) => `${like.user_id}:${like.entity_type}:${like.entity_id}`)).size,
    db.state.likes.length
  )
})

test('community writer generates requested current phase 2 moderation and growth samples with entity refs', async () => {
  const db = new FakeCommunityDb({
    nextUserId: 6,
    nextPostId: 4,
    nextCommentId: 4,
    users: [
      { id: 1, username: 'existing-user-1', email: 'existing-1@example.com' },
      { id: 2, username: 'existing-user-2', email: 'existing-2@example.com' },
      { id: 3, username: 'existing-user-3', email: 'existing-3@example.com' },
      { id: 4, username: 'existing-user-4', email: 'existing-4@example.com' },
      { id: 5, username: 'existing-user-5', email: 'existing-5@example.com' }
    ],
    posts: [
      { id: 1, user_id: 1, category_id: 2, title: 'Existing post 1', content: 'Already present', comment_count: 1, score: 42 },
      { id: 2, user_id: 2, category_id: 2, title: 'Existing post 2', content: 'Already present', comment_count: 1, score: 38 },
      { id: 3, user_id: 3, category_id: 1, title: 'Existing post 3', content: 'Already present', comment_count: 0, score: 28 }
    ],
    comments: [
      { id: 1, user_id: 2, entity_type: 1, entity_id: 1, target_id: 0, content: 'Existing direct comment' },
      { id: 2, user_id: 3, entity_type: 1, entity_id: 2, target_id: 0, content: 'Existing direct comment' },
      { id: 3, user_id: 4, entity_type: 2, entity_id: 2, target_id: 3, content: 'Existing reply comment' }
    ]
  })
  const entityRefRepository = createEntityRefRepositoryDouble()
  const writer = createCommunityWriter({
    db,
    entityRefRepository,
    now: (() => {
      let tick = 0
      const start = Date.parse('2026-03-25T10:00:00.000Z')
      return () => new Date(start + tick++ * 1000).toISOString()
    })()
  })

  const result = await writer.writePhase({
    batchId: 42,
    plan: createPhase2Plan()
  })

  assert.deepEqual(result.insertedCounts, {
    users: 0,
    posts: 0,
    comments: 0,
    socialFollows: 0,
    socialLikes: 0,
    reports: 2,
    moderationActions: 2,
    userTaskProgress: 4
  })

  const recordedRefs = await entityRefRepository.listByBatchId(42)
  assert.deepEqual(countByEntityType(recordedRefs), {
    reports: 2,
    moderation_actions: 2,
    user_task_progress: 4
  })
})

test('im writer generates coherent room and private message refs for requested counts', async () => {
  const db = new FakeCommunityDb({
    nextUserId: 7,
    users: [
      { id: 1, username: 'existing-user-1', email: 'existing-1@example.com' },
      { id: 2, username: 'existing-user-2', email: 'existing-2@example.com' },
      { id: 3, username: 'existing-user-3', email: 'existing-3@example.com' },
      { id: 4, username: 'existing-user-4', email: 'existing-4@example.com' },
      { id: 5, username: 'existing-user-5', email: 'existing-5@example.com' },
      { id: 6, username: 'existing-user-6', email: 'existing-6@example.com' }
    ]
  })
  const entityRefRepository = createEntityRefRepositoryDouble()
  const writer = createImWriter({
    db,
    entityRefRepository,
    now: (() => {
      let tick = 0
      const start = Date.parse('2026-03-25T11:00:00.000Z')
      return () => new Date(start + tick++ * 1000).toISOString()
    })()
  })

  const result = await writer.writePhase({
    batchId: 42,
    plan: createImPlan()
  })

  assert.deepEqual(result.insertedCounts, {
    imRooms: 2,
    imRoomMembers: 6,
    imRoomMessages: 5,
    imConversations: 3,
    imPrivateMessages: 7
  })

  const recordedRefs = await entityRefRepository.listByBatchId(42)
  assert.deepEqual(countByEntityType(recordedRefs), {
    im_rooms: 2,
    im_room_members: 6,
    im_room_messages: 5,
    im_conversations: 3,
    im_private_messages: 7
  })
  assert.ok(recordedRefs.filter((ref) => ref.entityType === 'im_room_members').every((ref) => /^\d+:\d+$/u.test(ref.entityKey)))
  assert.ok(recordedRefs.filter((ref) => ref.entityType === 'im_room_messages').every((ref) => /^\d+:\d+$/u.test(ref.entityKey)))
  assert.ok(
    recordedRefs.filter((ref) => ref.entityType === 'im_private_messages').every((ref) => /^\d+_\d+:\d+$/u.test(ref.entityKey))
  )
})

class FakeDeleteDb {
  constructor({
    reports = [],
    moderationActions = [],
    userTaskProgress = [],
    imRooms = [],
    imRoomMembers = [],
    imRoomMessages = [],
    imConversations = [],
    imPrivateMessages = [],
    users = [],
    posts = [],
    comments = [],
    follows = [],
    likes = [],
    demoBatch = [],
    demoJob = [],
    demoBatchTarget = [],
    demoEntityRef = []
  } = {}) {
    this.state = {
      reports: structuredClone(reports),
      moderationActions: structuredClone(moderationActions),
      userTaskProgress: structuredClone(userTaskProgress),
      imRooms: structuredClone(imRooms),
      imRoomMembers: structuredClone(imRoomMembers),
      imRoomMessages: structuredClone(imRoomMessages),
      imConversations: structuredClone(imConversations),
      imPrivateMessages: structuredClone(imPrivateMessages),
      users: structuredClone(users),
      posts: structuredClone(posts),
      comments: structuredClone(comments),
      follows: structuredClone(follows),
      likes: structuredClone(likes),
      demoBatch: structuredClone(demoBatch),
      demoJob: structuredClone(demoJob),
      demoBatchTarget: structuredClone(demoBatchTarget),
      demoEntityRef: structuredClone(demoEntityRef)
    }
    this.operationLog = []
  }

  async withTransaction(work) {
    return work(this)
  }

  async query(sql, params = []) {
    const normalized = normalizeSql(sql)

    if (normalized.startsWith('select id, entity_type, entity_id from comment where id = ?')) {
      const comment = this.state.comments.find((row) => row.id === params[0])
      return comment
        ? [
            {
              id: comment.id,
              entity_type: comment.entity_type,
              entity_id: comment.entity_id
            }
          ]
        : []
    }

    if (
      normalized.startsWith('select count(*) as comment_count from comment where status = 0') &&
      normalized.includes('entity_type = 1 and entity_id = ?') &&
      normalized.includes('entity_type = 2 and entity_id in')
    ) {
      const [postId] = params
      const directCommentIds = new Set(
        this.state.comments
          .filter((comment) => comment.status === 0 && comment.entity_type === 1 && comment.entity_id === postId)
          .map((comment) => comment.id)
      )
      const commentCount = this.state.comments.filter((comment) => {
        if (comment.status !== 0) {
          return false
        }

        if (comment.entity_type === 1) {
          return comment.entity_id === postId
        }

        return comment.entity_type === 2 && directCommentIds.has(comment.entity_id)
      }).length

      return [{ comment_count: commentCount }]
    }

    throw new Error(`Unsupported query: ${sql}`)
  }

  async execute(sql, params = []) {
    const normalized = normalizeSql(sql)
    this.#recordSnapshot(normalized)

    if (normalized.startsWith('delete from moderation_action where id = ?')) {
      const before = this.state.moderationActions.length
      this.state.moderationActions = this.state.moderationActions.filter((row) => row.id !== params[0])
      return { affectedRows: before - this.state.moderationActions.length }
    }

    if (normalized.startsWith('delete from report where id = ?')) {
      const before = this.state.reports.length
      this.state.reports = this.state.reports.filter((row) => row.id !== params[0])
      return { affectedRows: before - this.state.reports.length }
    }

    if (normalized.startsWith('delete from user_task_progress where id = ?')) {
      const before = this.state.userTaskProgress.length
      this.state.userTaskProgress = this.state.userTaskProgress.filter((row) => row.id !== params[0])
      return { affectedRows: before - this.state.userTaskProgress.length }
    }

    if (normalized.startsWith('delete from im_core.im_private_message where conversation_id = ? and seq = ?')) {
      const before = this.state.imPrivateMessages.length
      this.state.imPrivateMessages = this.state.imPrivateMessages.filter(
        (row) => !(row.conversation_id === params[0] && row.seq === params[1])
      )
      return { affectedRows: before - this.state.imPrivateMessages.length }
    }

    if (normalized.startsWith('delete from im_core.im_conversation where conversation_id = ?')) {
      const before = this.state.imConversations.length
      this.state.imConversations = this.state.imConversations.filter((row) => row.conversation_id !== params[0])
      return { affectedRows: before - this.state.imConversations.length }
    }

    if (normalized.startsWith('delete from im_core.im_room_message where room_id = ? and seq = ?')) {
      const before = this.state.imRoomMessages.length
      this.state.imRoomMessages = this.state.imRoomMessages.filter(
        (row) => !(row.room_id === params[0] && row.seq === params[1])
      )
      return { affectedRows: before - this.state.imRoomMessages.length }
    }

    if (normalized.startsWith('delete from im_core.im_room_member where room_id = ? and user_id = ?')) {
      const before = this.state.imRoomMembers.length
      this.state.imRoomMembers = this.state.imRoomMembers.filter(
        (row) => !(row.room_id === params[0] && row.user_id === params[1])
      )
      return { affectedRows: before - this.state.imRoomMembers.length }
    }

    if (normalized.startsWith('delete from im_core.im_room where room_id = ?')) {
      const before = this.state.imRooms.length
      this.state.imRooms = this.state.imRooms.filter((row) => row.room_id !== params[0])
      return { affectedRows: before - this.state.imRooms.length }
    }

    if (normalized.startsWith('delete from social_like where user_id = ? and entity_type = ? and entity_id = ?')) {
      const before = this.state.likes.length
      this.state.likes = this.state.likes.filter(
        (row) => !(row.user_id === params[0] && row.entity_type === params[1] && row.entity_id === params[2])
      )
      return { affectedRows: before - this.state.likes.length }
    }

    if (normalized.startsWith('delete from social_follow where user_id = ? and entity_type = ? and entity_id = ?')) {
      const before = this.state.follows.length
      this.state.follows = this.state.follows.filter(
        (row) => !(row.user_id === params[0] && row.entity_type === params[1] && row.entity_id === params[2])
      )
      return { affectedRows: before - this.state.follows.length }
    }

    if (normalized.startsWith('delete from comment where id = ?')) {
      const before = this.state.comments.length
      this.state.comments = this.state.comments.filter((row) => row.id !== params[0])
      return { affectedRows: before - this.state.comments.length }
    }

    if (normalized.startsWith('delete from discuss_post where id = ?')) {
      const before = this.state.posts.length
      this.state.posts = this.state.posts.filter((row) => row.id !== params[0])
      return { affectedRows: before - this.state.posts.length }
    }

    if (normalized.startsWith('update discuss_post set comment_count = ? where id = ?')) {
      const post = this.state.posts.find((row) => row.id === params[1])

      if (!post) {
        return { affectedRows: 0 }
      }

      post.comment_count = params[0]
      return { affectedRows: 1 }
    }

    if (normalized.startsWith('delete from user where id = ?')) {
      const before = this.state.users.length
      this.state.users = this.state.users.filter((row) => row.id !== params[0])
      return { affectedRows: before - this.state.users.length }
    }

    if (normalized.startsWith('delete from demo_entity_ref where batch_id = ?')) {
      const before = this.state.demoEntityRef.length
      this.state.demoEntityRef = this.state.demoEntityRef.filter((row) => row.batch_id !== normalizeDbId(params[0]))
      return { affectedRows: before - this.state.demoEntityRef.length }
    }

    if (normalized.startsWith('delete from demo_batch_target where batch_id = ?')) {
      const before = this.state.demoBatchTarget.length
      this.state.demoBatchTarget = this.state.demoBatchTarget.filter(
        (row) => row.batch_id !== normalizeDbId(params[0])
      )
      return { affectedRows: before - this.state.demoBatchTarget.length }
    }

    if (normalized.startsWith('delete from demo_job where batch_id = ?')) {
      const before = this.state.demoJob.length
      this.state.demoJob = this.state.demoJob.filter((row) => row.batch_id !== normalizeDbId(params[0]))
      return { affectedRows: before - this.state.demoJob.length }
    }

    if (normalized.startsWith('delete from demo_batch where id = ?')) {
      const before = this.state.demoBatch.length
      this.state.demoBatch = this.state.demoBatch.filter((row) => row.id !== normalizeDbId(params[0]))
      return { affectedRows: before - this.state.demoBatch.length }
    }

    throw new Error(`Unsupported execute: ${sql}`)
  }

  #recordSnapshot(sql) {
    this.operationLog.push({
      sql,
      businessCounts: {
        reports: this.state.reports.length,
        moderationActions: this.state.moderationActions.length,
        userTaskProgress: this.state.userTaskProgress.length,
        imPrivateMessages: this.state.imPrivateMessages.length,
        imConversations: this.state.imConversations.length,
        imRoomMessages: this.state.imRoomMessages.length,
        imRoomMembers: this.state.imRoomMembers.length,
        imRooms: this.state.imRooms.length,
        socialLikes: this.state.likes.length,
        socialFollows: this.state.follows.length,
        comments: this.state.comments.length,
        posts: this.state.posts.length,
        users: this.state.users.length
      },
      metadataCounts: {
        entityRefs: this.state.demoEntityRef.length,
        targets: this.state.demoBatchTarget.length,
        jobs: this.state.demoJob.length,
        batches: this.state.demoBatch.length
      }
    })
  }
}

function createDeleteServiceHarness() {
  const batchId = metadataId(42)
  const db = new FakeDeleteDb({
    users: [{ id: 1001 }],
    posts: [{ id: 2001, user_id: 1001, comment_count: 2 }],
    comments: [
      { id: 3001, entity_type: 1, entity_id: 2001, user_id: 1001, status: 0 },
      { id: 3002, entity_type: 2, entity_id: 3001, user_id: 1001, status: 0 }
    ],
    follows: [{ user_id: 1001, entity_type: 3, entity_id: 1001 }],
    likes: [{ user_id: 1001, entity_type: 1, entity_id: 2001 }],
    demoBatch: [{ id: batchId }],
    demoJob: [{ id: metadataId(501), batch_id: batchId, status: 'succeeded' }],
    demoBatchTarget: [{ id: metadataId(601), batch_id: batchId }],
    demoEntityRef: [
      { id: metadataId(701), batch_id: batchId, entity_type: 'users', entity_key: '1001' },
      { id: metadataId(702), batch_id: batchId, entity_type: 'posts', entity_key: '2001' },
      { id: metadataId(703), batch_id: batchId, entity_type: 'comments', entity_key: '3001' },
      { id: metadataId(704), batch_id: batchId, entity_type: 'comments', entity_key: '3002' },
      { id: metadataId(705), batch_id: batchId, entity_type: 'social_follows', entity_key: '1001:3:1001' },
      { id: metadataId(706), batch_id: batchId, entity_type: 'social_likes', entity_key: '1001:1:2001' }
    ]
  })

  const service = createDeleteBatchService({
    db,
    batchRepository: {
      async findById(id) {
        return id === batchId ? { id: batchId, status: 'succeeded' } : null
      }
    },
    jobRepository: {
      async listByBatchId(id) {
        return id === batchId ? [{ id: 501, batchId, status: 'succeeded' }] : []
      }
    },
    entityRefRepository: {
      async listByBatchId(id) {
        return id === batchId
          ? [
              { entityType: 'users', entityKey: '1001' },
              { entityType: 'posts', entityKey: '2001' },
              { entityType: 'comments', entityKey: '3001' },
              { entityType: 'comments', entityKey: '3002' },
              { entityType: 'social_follows', entityKey: '1001:3:1001' },
              { entityType: 'social_likes', entityKey: '1001:1:2001' }
            ]
          : []
      }
    }
  })

  return {
    batchId,
    db,
    service
  }
}

function createSurvivingPostDeleteHarness({ jobStatus = 'succeeded' } = {}) {
  const batchId = metadataId(77)
  const db = new FakeDeleteDb({
    users: [{ id: 9001 }],
    posts: [{ id: 500, user_id: 7, comment_count: 4 }],
    comments: [
      { id: 410, entity_type: 1, entity_id: 500, user_id: 9001, status: 0 },
      { id: 411, entity_type: 2, entity_id: 410, user_id: 9001, status: 0 },
      { id: 412, entity_type: 1, entity_id: 500, user_id: 2, status: 0 },
      { id: 413, entity_type: 2, entity_id: 412, user_id: 3, status: 0 }
    ],
    demoBatch: [{ id: batchId }],
    demoJob: [{ id: metadataId(801), batch_id: batchId, status: jobStatus }],
    demoBatchTarget: [{ id: metadataId(901), batch_id: batchId }],
    demoEntityRef: [
      { id: metadataId(1001), batch_id: batchId, entity_type: 'comments', entity_key: '410' },
      { id: metadataId(1002), batch_id: batchId, entity_type: 'comments', entity_key: '411' }
    ]
  })

  const service = createDeleteBatchService({
    db,
    batchRepository: {
      async findById(id) {
        return id === batchId ? { id: batchId, status: jobStatus } : null
      }
    },
    jobRepository: {
      async listByBatchId(id) {
        return id === batchId ? [{ id: 801, batchId, status: jobStatus }] : []
      }
    },
    entityRefRepository: {
      async listByBatchId(id) {
        return id === batchId
          ? [
              { entityType: 'comments', entityKey: '410' },
              { entityType: 'comments', entityKey: '411' }
            ]
          : []
      }
    }
  })

  return {
    batchId,
    db,
    service
  }
}

function createPhase2DeleteHarness() {
  const batchId = metadataId(99)
  const db = new FakeDeleteDb({
    reports: [{ id: 21, reporter_id: 2, target_type: 1, target_id: 2001, status: 0 }],
    moderationActions: [{ id: 31, report_id: 21, actor_id: 9001, action: 'WARN' }],
    userTaskProgress: [{ id: 51, user_id: 2, task_code: 'DAILY_CHECK_IN', period_key: '2026-03-25' }],
    imRooms: [{ room_id: 101, name: 'Demo Room 1', last_seq: 2 }],
    imRoomMembers: [
      { room_id: 101, user_id: 2, role: 1 },
      { room_id: 101, user_id: 3, role: 0 }
    ],
    imRoomMessages: [
      { room_id: 101, seq: 1, message_id: 10001 },
      { room_id: 101, seq: 2, message_id: 10002 }
    ],
    imConversations: [{ conversation_id: '2_4', user_a: 2, user_b: 4, last_seq: 1 }],
    imPrivateMessages: [{ conversation_id: '2_4', seq: 1, message_id: 20001 }],
    demoBatch: [{ id: batchId }],
    demoJob: [{ id: metadataId(1501), batch_id: batchId, status: 'succeeded' }],
    demoBatchTarget: [{ id: metadataId(1601), batch_id: batchId }],
    demoEntityRef: [
      { id: metadataId(1703), batch_id: batchId, entity_type: 'reports', entity_key: '21' },
      { id: metadataId(1704), batch_id: batchId, entity_type: 'moderation_actions', entity_key: '31' },
      { id: metadataId(1706), batch_id: batchId, entity_type: 'user_task_progress', entity_key: '51' },
      { id: metadataId(1712), batch_id: batchId, entity_type: 'im_private_messages', entity_key: '2_4:1' },
      { id: metadataId(1713), batch_id: batchId, entity_type: 'im_conversations', entity_key: '2_4' },
      { id: metadataId(1714), batch_id: batchId, entity_type: 'im_room_messages', entity_key: '101:1' },
      { id: metadataId(1715), batch_id: batchId, entity_type: 'im_room_messages', entity_key: '101:2' },
      { id: metadataId(1716), batch_id: batchId, entity_type: 'im_room_members', entity_key: '101:2' },
      { id: metadataId(1717), batch_id: batchId, entity_type: 'im_room_members', entity_key: '101:3' },
      { id: metadataId(1718), batch_id: batchId, entity_type: 'im_rooms', entity_key: '101' }
    ]
  })

  const service = createDeleteBatchService({
    db,
    batchRepository: {
      async findById(id) {
        return id === batchId ? { id: batchId, status: 'succeeded' } : null
      }
    },
    jobRepository: {
      async listByBatchId(id) {
        return id === batchId ? [{ id: 1501, batchId, status: 'succeeded' }] : []
      }
    },
    entityRefRepository: {
      async listByBatchId(id) {
        return id === batchId
          ? [
              { entityType: 'reports', entityKey: '21' },
              { entityType: 'moderation_actions', entityKey: '31' },
              { entityType: 'user_task_progress', entityKey: '51' },
              { entityType: 'im_private_messages', entityKey: '2_4:1' },
              { entityType: 'im_conversations', entityKey: '2_4' },
              { entityType: 'im_room_messages', entityKey: '101:1' },
              { entityType: 'im_room_messages', entityKey: '101:2' },
              { entityType: 'im_room_members', entityKey: '101:2' },
              { entityType: 'im_room_members', entityKey: '101:3' },
              { entityType: 'im_rooms', entityKey: '101' }
            ]
          : []
      }
    }
  })

  return {
    batchId,
    db,
    service
  }
}

test('delete batch removes dependent business rows before parents and metadata', async () => {
  const { service, db, batchId } = createDeleteServiceHarness()

  const result = await service.deleteBatch(batchId)

  assert.deepEqual(
    db.operationLog.map((entry) => entry.sql),
    [
      'delete from social_like where user_id = ? and entity_type = ? and entity_id = ?',
      'delete from social_follow where user_id = ? and entity_type = ? and entity_id = ?',
      'delete from comment where id = ?',
      'delete from comment where id = ?',
      'delete from discuss_post where id = ?',
      'delete from user where id = ?',
      'update discuss_post set comment_count = ? where id = ?',
      'delete from demo_entity_ref where batch_id = ?',
      'delete from demo_batch_target where batch_id = ?',
      'delete from demo_job where batch_id = ?',
      'delete from demo_batch where id = ?'
    ]
  )
  assert.deepEqual(result, {
    batchId,
    deleted: {
      business: {
        reports: 0,
        moderationActions: 0,
        userTaskProgress: 0,
        imPrivateMessages: 0,
        imConversations: 0,
        imRoomMessages: 0,
        imRoomMembers: 0,
        imRooms: 0,
        socialLikes: 1,
        socialFollows: 1,
        comments: 2,
        posts: 1,
        users: 1
      },
      metadata: {
        entityRefs: 6,
        targets: 1,
        jobs: 1,
        batches: 1
      }
    }
  })
})

test('delete batch keeps metadata rows until all business rows are gone', async () => {
  const { service, db, batchId } = createDeleteServiceHarness()

  await service.deleteBatch(batchId)

  const metadataDeleteSnapshot = db.operationLog.find(
    (entry) => entry.sql === 'delete from demo_entity_ref where batch_id = ?'
  )

  assert.deepEqual(metadataDeleteSnapshot.businessCounts, {
    reports: 0,
    moderationActions: 0,
    userTaskProgress: 0,
    imPrivateMessages: 0,
    imConversations: 0,
    imRoomMessages: 0,
    imRoomMembers: 0,
    imRooms: 0,
    socialLikes: 0,
    socialFollows: 0,
    comments: 0,
    posts: 0,
    users: 0
  })
  assert.deepEqual(metadataDeleteSnapshot.metadataCounts, {
    entityRefs: 6,
    targets: 1,
    jobs: 1,
    batches: 1
  })
  assert.equal(db.state.demoEntityRef.length, 0)
  assert.equal(db.state.demoBatchTarget.length, 0)
  assert.equal(db.state.demoJob.length, 0)
  assert.equal(db.state.demoBatch.length, 0)
  assert.equal(db.state.likes.length, 0)
  assert.equal(db.state.follows.length, 0)
  assert.equal(db.state.comments.length, 0)
  assert.equal(db.state.posts.length, 0)
  assert.equal(db.state.users.length, 0)
})

test('delete batch blocks pending jobs as nonterminal work', async () => {
  const { service, batchId } = createSurvivingPostDeleteHarness({
    jobStatus: 'pending'
  })

  await assert.rejects(
    () => service.deleteBatch(batchId),
    (error) => {
      assert.equal(error.code, 'BATCH_JOB_RUNNING')
      assert.equal(error.status, 409)
      assert.equal(error.runningJob?.status, 'pending')
      return true
    }
  )
})

test('delete batch recomputes comment_count for surviving existing posts', async () => {
  const { service, db, batchId } = createSurvivingPostDeleteHarness()

  const result = await service.deleteBatch(batchId)

  assert.equal(db.state.posts[0].id, 500)
  assert.equal(db.state.posts[0].comment_count, 2)
  assert.deepEqual(
    db.state.comments.map((comment) => comment.id),
    [412, 413]
  )
  assert.equal(result.deleted.business.comments, 2)
  assert.ok(
    db.operationLog.some((entry) => entry.sql === 'update discuss_post set comment_count = ? where id = ?')
  )
})

test('delete batch reports current phase 2 moderation, growth, and im counts in deleted summaries', async () => {
  const { service, batchId } = createPhase2DeleteHarness()

  const result = await service.deleteBatch(batchId)

  assert.deepEqual(result.deleted.business, {
    reports: 1,
    moderationActions: 1,
    userTaskProgress: 1,
    imPrivateMessages: 1,
    imConversations: 1,
    imRoomMessages: 2,
    imRoomMembers: 2,
    imRooms: 1,
    socialLikes: 0,
    socialFollows: 0,
    comments: 0,
    posts: 0,
    users: 0
  })
})
