import assert from 'node:assert/strict'
import test from 'node:test'

import { generateCommunityPhaseDataset } from '../src/generator/contentGenerator.mjs'
import { createPlanner } from '../src/generator/planner.mjs'

function createConfig(overrides = {}) {
  return {
    autoFill: {
      sceneKey: 'tech-community-hot-start',
      defaults: {
        users: 100,
        posts: 800,
        comments: 2500
      },
      ...overrides
    }
  }
}

function createTargetRepository(initialTargets = []) {
  const store = {
    targets: initialTargets.map((target, index) => ({
      id: index + 1,
      batchId: 1,
      ...target
    })),
    replaceCalls: []
  }

  return {
    store,
    async listByBatchId() {
      return structuredClone(store.targets)
    },
    async replaceForBatch(batchId, targets) {
      store.replaceCalls.push({
        batchId,
        targets: structuredClone(targets)
      })
      store.targets = targets.map((target, index) => ({
        id: index + 1,
        batchId,
        ...target
      }))
      return structuredClone(store.targets)
    }
  }
}

function createEntityRefRepository(counts) {
  const refs = Object.entries(counts).flatMap(([entityType, count]) =>
    Array.from({ length: count }, (_, index) => ({
      id: `${entityType}-${index + 1}`,
      batchId: 1,
      entityType,
      entityKey: `${entityType}-${index + 1}`,
      createdAt: '2026-03-25T00:00:00.000Z'
    }))
  )

  return {
    async listByBatchId() {
      return structuredClone(refs)
    }
  }
}

function formatEntityRef(ref) {
  return `${ref.kind}:${ref.id}`
}

function createCommunityPlan(overrides = {}) {
  return {
    batchId: 7,
    sceneKey: 'tech-community-hot-start',
    deficits: {
      users: 4,
      posts: 6,
      comments: 9
    },
    phases: [
      {
        name: 'community',
        deficits: {
          users: 4,
          posts: 6,
          comments: 9
        },
        needsWork: true
      }
    ],
    ...overrides
  }
}

test('planner computes default batch deficits as target minus existing refs', async () => {
  const targetRepository = createTargetRepository()
  const planner = createPlanner({
    config: createConfig(),
    targetRepository,
    entityRefRepository: createEntityRefRepository({
      users: 20,
      posts: 200,
      comments: 400,
      reports: 8,
      moderation_actions: 3,
      user_task_progress: 6,
      im_rooms: 2,
      im_room_members: 6,
      im_room_messages: 15,
      im_conversations: 4,
      im_private_messages: 18
    })
  })

  const plan = await planner.planDefaultBatch({
    batchId: 1
  })

  assert.deepEqual(plan.deficits, {
    users: 80,
    posts: 600,
    comments: 2100,
    im_rooms: 8,
    im_room_members: 24,
    im_room_messages: 105,
    im_conversations: 46,
    im_private_messages: 162,
    user_task_progress: 194,
    reports: 32,
    moderation_actions: 17
  })
  assert.equal(plan.needsWork, true)
  assert.deepEqual(
    plan.phases.map((phase) => phase.name),
    ['community', 'im', 'growth', 'moderation']
  )
  assert.deepEqual(plan.phases[0].deficits, {
    users: 80,
    posts: 600,
    comments: 2100
  })
  assert.deepEqual(targetRepository.store.replaceCalls[0].targets, [
    {
      entityType: 'users',
      targetKey: 'tech-community-hot-start.users',
      targetCount: 100,
      payloadJson: {
        phase: 'community',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'posts',
      targetKey: 'tech-community-hot-start.posts',
      targetCount: 800,
      payloadJson: {
        phase: 'community',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'comments',
      targetKey: 'tech-community-hot-start.comments',
      targetCount: 2500,
      payloadJson: {
        phase: 'community',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'im_rooms',
      targetKey: 'tech-community-hot-start.im_rooms',
      targetCount: 10,
      payloadJson: {
        phase: 'im',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'im_room_members',
      targetKey: 'tech-community-hot-start.im_room_members',
      targetCount: 30,
      payloadJson: {
        phase: 'im',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'im_room_messages',
      targetKey: 'tech-community-hot-start.im_room_messages',
      targetCount: 120,
      payloadJson: {
        phase: 'im',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'im_conversations',
      targetKey: 'tech-community-hot-start.im_conversations',
      targetCount: 50,
      payloadJson: {
        phase: 'im',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'im_private_messages',
      targetKey: 'tech-community-hot-start.im_private_messages',
      targetCount: 180,
      payloadJson: {
        phase: 'im',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'user_task_progress',
      targetKey: 'tech-community-hot-start.user_task_progress',
      targetCount: 200,
      payloadJson: {
        phase: 'growth',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'reports',
      targetKey: 'tech-community-hot-start.reports',
      targetCount: 40,
      payloadJson: {
        phase: 'moderation',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'moderation_actions',
      targetKey: 'tech-community-hot-start.moderation_actions',
      targetCount: 20,
      payloadJson: {
        phase: 'moderation',
        sceneKey: 'tech-community-hot-start'
      }
    }
  ])
})

test('planner becomes a no-op when existing counts already satisfy all targets', async () => {
  const targetRepository = createTargetRepository([
    {
      entityType: 'users',
      targetKey: 'tech-community-hot-start.users',
      targetCount: 10,
      payloadJson: {
        phase: 'community',
        sceneKey: 'tech-community-hot-start'
      }
    },
    {
      entityType: 'posts',
      targetKey: 'tech-community-hot-start.posts',
      targetCount: 5,
      payloadJson: {
        phase: 'community',
        sceneKey: 'tech-community-hot-start'
      }
    }
  ])
  const planner = createPlanner({
    config: createConfig(),
    targetRepository,
    entityRefRepository: createEntityRefRepository({
      users: 12,
      posts: 5
    })
  })

  const plan = await planner.planDefaultBatch({
    batchId: 1
  })

  assert.equal(plan.needsWork, false)
  assert.deepEqual(plan.deficits, {
    users: 0,
    posts: 0
  })
  assert.equal(plan.totalDeficitCount, 0)
  assert.deepEqual(
    plan.phases.map(({ name, totalDeficitCount }) => [name, totalDeficitCount]),
    [
      ['community', 0],
      ['im', 0],
      ['growth', 0],
      ['moderation', 0]
    ]
  )
  assert.equal(targetRepository.store.replaceCalls.length, 0)
})

test('phase 1 generator produces requested counts with valid comment and social references', () => {
  const plan = createCommunityPlan()
  const existingUsers = [{ id: 801 }, { id: 802 }]
  const existingPosts = [{ id: 901 }, { id: 902 }]
  const existingComments = [
    {
      id: 1001,
      postId: 901,
      userId: 801
    }
  ]

  const generated = generateCommunityPhaseDataset({
    plan,
    existing: {
      users: existingUsers,
      posts: existingPosts,
      comments: existingComments
    }
  })

  assert.equal(generated.users.length, 4)
  assert.equal(generated.posts.length, 6)
  assert.equal(generated.comments.length, 9)
  assert.ok(generated.follows.length > 0)
  assert.ok(generated.likes.length > 0)

  const validUserRefs = new Set([
    ...existingUsers.map((user) => `existing:${user.id}`),
    ...generated.users.map((user, index) => `generated:${index}`)
  ])
  const validPostRefs = new Set([
    ...existingPosts.map((post) => `existing:${post.id}`),
    ...generated.posts.map((post, index) => `generated:${index}`)
  ])
  const validCommentRefs = new Set([
    ...existingComments.map((comment) => `existing:${comment.id}`),
    ...generated.comments.map((comment, index) => `generated:${index}`)
  ])

  for (const comment of generated.comments) {
    assert.ok(validUserRefs.has(formatEntityRef(comment.authorRef)))
    assert.ok(validPostRefs.has(formatEntityRef(comment.postRef)))
    assert.ok(comment.depth >= 1)
    assert.ok(comment.depth <= 2)

    if (comment.parentCommentRef) {
      assert.equal(comment.depth, 2)
      assert.ok(validCommentRefs.has(formatEntityRef(comment.parentCommentRef)))
    }
  }

  for (const follow of generated.follows) {
    const followerRef = formatEntityRef(follow.followerUserRef)
    const followedRef = formatEntityRef(follow.followedUserRef)
    assert.ok(validUserRefs.has(followerRef))
    assert.ok(validUserRefs.has(followedRef))
    assert.notEqual(followerRef, followedRef)
  }

  for (const like of generated.likes) {
    assert.ok(validUserRefs.has(formatEntityRef(like.userRef)))

    if (like.entityType === 'posts') {
      assert.ok(validPostRefs.has(formatEntityRef(like.entityRef)))
      continue
    }

    assert.equal(like.entityType, 'comments')
    assert.ok(validCommentRefs.has(formatEntityRef(like.entityRef)))
  }

  assert.deepEqual(
    generated,
    generateCommunityPhaseDataset({
      plan,
      existing: {
        users: existingUsers,
        posts: existingPosts,
        comments: existingComments
      }
    })
  )
})

test('phase 1 generator offsets generated usernames and emails for incremental top-ups', () => {
  const plan = createCommunityPlan({
    deficits: {
      users: 2,
      posts: 0,
      comments: 0
    },
    phases: [
      {
        name: 'community',
        deficits: {
          users: 2,
          posts: 0,
          comments: 0
        },
        needsWork: true
      }
    ]
  })

  const firstRun = generateCommunityPhaseDataset({
    plan,
    existing: {
      users: [{ id: 101 }, { id: 102 }],
      posts: [],
      comments: []
    }
  })

  const secondRun = generateCommunityPhaseDataset({
    plan,
    existing: {
      users: [{ id: 101 }, { id: 102 }, { id: 103 }, { id: 104 }],
      posts: [],
      comments: []
    }
  })

  assert.ok(firstRun.users.every((user) => /^demo_7_(3|4)_\d+$/u.test(user.username)))
  assert.ok(secondRun.users.every((user) => /^demo_7_(5|6)_\d+$/u.test(user.username)))
  assert.equal(
    firstRun.users.some((user) => secondRun.users.some((nextUser) => nextUser.username === user.username)),
    false
  )
  assert.equal(
    firstRun.users.some((user) => secondRun.users.some((nextUser) => nextUser.email === user.email)),
    false
  )
})
