import assert from 'node:assert/strict'
import test from 'node:test'

import { bufferToUuid } from '../src/db/uuidv7.mjs'
import { generateCommunityPhaseDataset } from '../src/generator/contentGenerator.mjs'
import { createCommunityWriter } from '../src/writers/communityWriter.mjs'
import { createDeleteBatchService } from '../src/writers/deleteBatchService.mjs'
import { createImWriter } from '../src/writers/imWriter.mjs'
import { FakeMysqlDb } from './support/fakeMysql.mjs'

function metadataId(sequence) {
  return `01965429-b34a-7000-8000-${String(sequence).padStart(12, '0')}`
}

function normalizeDbId(value) {
  return bufferToUuid(value)
}

class FakeCommunityDb extends FakeMysqlDb {
  constructor(overrides = {}) {
    const {
      nextUserId = 2,
      nextPostId = 2,
      nextCommentId = 2,
      nextReportId = 1,
      nextModerationActionId = 1,
      nextUserTaskProgressId = 1,
      ...tableOverrides
    } = structuredClone(overrides)
    super({
      state: {
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
        ...tableOverrides
      },
      nextIds: {
        users: nextUserId,
        posts: nextPostId,
        comments: nextCommentId,
        reports: nextReportId,
        moderationActions: nextModerationActionId,
        userTaskProgress: nextUserTaskProgressId
      },
      aliases: {
        user: 'users', discuss_post: 'posts', comment: 'comments', category: 'categories',
        social_follow: 'follows', social_like: 'likes', report: 'reports',
        moderation_action: 'moderationActions', user_task_progress: 'userTaskProgress',
        'im_core.im_room': 'imRooms', 'im_core.im_room_member': 'imRoomMembers',
        'im_core.im_room_message': 'imRoomMessages', 'im_core.im_conversation': 'imConversations',
        'im_core.im_private_message': 'imPrivateMessages'
      },
      autoIds: {
        user: { field: 'id' }, discuss_post: { field: 'id' }, comment: { field: 'id' },
        report: { field: 'id' }, moderation_action: { field: 'id' }, user_task_progress: { field: 'id' }
      },
      uniqueKeys: {
        user: [['username'], ['email']],
        social_follow: [['user_id', 'entity_type', 'entity_id']],
        social_like: [['user_id', 'entity_type', 'entity_id']]
      }
    })
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
  assert.equal(entityRefRepository.appendCalls.length, 1)
  assert.deepEqual(entityRefRepository.appendCalls[0].refs, result.generatedRefs)

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
  assert.equal(entityRefRepository.appendCalls.length, 1)
  assert.deepEqual(entityRefRepository.appendCalls[0].refs, result.generatedRefs)
})

test('community writer appends every mixed-phase entity ref exactly once', async () => {
  const db = new FakeCommunityDb()
  const entityRefRepository = createEntityRefRepositoryDouble()
  const writer = createCommunityWriter({ db, entityRefRepository })

  const result = await writer.writePhase({
    batchId: 42,
    plan: createPhase2Plan({
      communityDeficits: {
        users: 2,
        posts: 2,
        comments: 3
      }
    })
  })

  assert.equal(entityRefRepository.appendCalls.length, 1)
  assert.deepEqual(entityRefRepository.appendCalls[0].refs, result.generatedRefs)
  assert.equal(
    new Set(result.generatedRefs.map((ref) => `${ref.entityType}:${ref.entityKey}`)).size,
    result.generatedRefs.length
  )
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

class FakeDeleteDb extends FakeMysqlDb {
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
    demoBatchTarget = [],
    demoEntityRef = []
  } = {}) {
    super({
      state: {
        reports,
        moderationActions,
        userTaskProgress,
        imRooms,
        imRoomMembers,
        imRoomMessages,
        imConversations,
        imPrivateMessages,
        users,
        posts,
        comments,
        follows,
        likes,
        demoBatch,
        demoBatchTarget,
        demoEntityRef
      },
      aliases: {
        moderation_action: 'moderationActions',
        report: 'reports',
        user_task_progress: 'userTaskProgress',
        'im_core.im_private_message': 'imPrivateMessages',
        'im_core.im_conversation': 'imConversations',
        'im_core.im_room_message': 'imRoomMessages',
        'im_core.im_room_member': 'imRoomMembers',
        'im_core.im_room': 'imRooms',
        social_like: 'likes',
        social_follow: 'follows',
        comment: 'comments',
        discuss_post: 'posts',
        user: 'users',
        demo_entity_ref: 'demoEntityRef',
        demo_batch_target: 'demoBatchTarget',
        demo_batch: 'demoBatch'
      },
      normalize: (value) => {
        try {
          return normalizeDbId(value)
        } catch {
          return value
        }
      },
      queryHandler: ({ db, sql, params }) => {
        if (sql.startsWith('select count(*) as comment_count from comment where status = 0')) {
          const [postId] = params
          const directCommentIds = new Set(
            db.state.comments
              .filter((comment) => comment.status === 0 && comment.entity_type === 1 && comment.entity_id === postId)
              .map((comment) => comment.id)
          )
          return [{
            comment_count: db.state.comments.filter((comment) => comment.status === 0 && (
              comment.entity_type === 1
                ? comment.entity_id === postId
                : comment.entity_type === 2 && directCommentIds.has(comment.entity_id)
            )).length
          }]
        }
      },
      recordSnapshot: ({ db, sql }) => {
        this.operationLog.push({
          sql,
          businessCounts: {
            reports: db.state.reports.length,
            moderationActions: db.state.moderationActions.length,
            userTaskProgress: db.state.userTaskProgress.length,
            imPrivateMessages: db.state.imPrivateMessages.length,
            imConversations: db.state.imConversations.length,
            imRoomMessages: db.state.imRoomMessages.length,
            imRoomMembers: db.state.imRoomMembers.length,
            imRooms: db.state.imRooms.length,
            socialLikes: db.state.likes.length,
            socialFollows: db.state.follows.length,
            comments: db.state.comments.length,
            posts: db.state.posts.length,
            users: db.state.users.length
          },
          metadataCounts: {
            entityRefs: db.state.demoEntityRef.length,
            targets: db.state.demoBatchTarget.length,
            batches: db.state.demoBatch.length
          }
        })
      }
    })
    this.operationLog = []
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

function createSurvivingPostDeleteHarness(status = 'succeeded') {
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
        return id === batchId ? { id: batchId, status } : null
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
    batches: 1
  })
  assert.equal(db.state.demoEntityRef.length, 0)
  assert.equal(db.state.demoBatchTarget.length, 0)
  assert.equal(db.state.demoBatch.length, 0)
  assert.equal(db.state.likes.length, 0)
  assert.equal(db.state.follows.length, 0)
  assert.equal(db.state.comments.length, 0)
  assert.equal(db.state.posts.length, 0)
  assert.equal(db.state.users.length, 0)
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

test('delete batch rejects a batch still being generated', async () => {
  const { service, batchId } = createSurvivingPostDeleteHarness('running')
  await assert.rejects(() => service.deleteBatch(batchId), { code: 'BATCH_RUNNING' })
})

test('delete batch force-cleans a stale running batch', async () => {
  const { service, db, batchId } = createSurvivingPostDeleteHarness('running')
  await service.deleteBatch(batchId, { force: true })
  assert.equal(db.state.demoBatch.length, 0)
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
