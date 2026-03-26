import { createSeededRandom } from './random.mjs'

const postTitlePrefixes = ['实战', '踩坑', '复盘', '整理', '总结', '观察']
const postSubjects = ['Spring Boot', 'MySQL', '搜索重建', '评论树', '性能排查', '社区冷启动']
const postActions = ['怎么做', '避坑指南', '设计取舍', '最佳实践', '调优记录', '经验分享']
const postBodyLeadIns = [
  '今天把一条真实的开发链路拆开来看，',
  '为了让首页内容更像真实社区，',
  '这次我把问题分成数据、接口和可见性三个层面，',
  '如果你也遇到类似场景，可以先从最容易观察的指标入手，'
]
const commentOpeners = ['这个点很关键，', '补充一个细节，', '我也踩过这个坑，', '这里可以再展开一下，']
const commentClosers = ['欢迎继续讨论。', '这样改动会更稳。', '这个方案在本地演示里足够用了。', '后面还能继续扩展。']
const avatarPool = [1, 2, 3, 4, 5, 6, 7, 8]

function normalizeCount(value) {
  return Math.max(0, Number.parseInt(value, 10) || 0)
}

function communityDeficitsFromPlan(plan) {
  const communityPhase = plan?.phases?.find((phase) => phase.name === 'community')
  return {
    users: normalizeCount(communityPhase?.deficits?.users ?? plan?.deficits?.users ?? 0),
    posts: normalizeCount(communityPhase?.deficits?.posts ?? plan?.deficits?.posts ?? 0),
    comments: normalizeCount(communityPhase?.deficits?.comments ?? plan?.deficits?.comments ?? 0)
  }
}

function createRef(kind, id) {
  return {
    kind,
    id
  }
}

function normalizeExisting(existing = {}) {
  return {
    users: (existing.users ?? []).map((user) => ({ id: Number(user.id) })),
    posts: (existing.posts ?? []).map((post) => ({ id: Number(post.id), commentCount: normalizeCount(post.commentCount) })),
    comments: (existing.comments ?? []).map((comment) => ({
      id: Number(comment.id),
      postId: Number(comment.postId),
      userId: Number(comment.userId)
    })),
    follows: (existing.follows ?? []).map((follow) => ({
      userId: Number(follow.userId),
      entityType: Number(follow.entityType),
      entityId: Number(follow.entityId)
    })),
    likes: (existing.likes ?? []).map((like) => ({
      userId: Number(like.userId),
      entityType: like.entityType,
      entityId: Number(like.entityId)
    }))
  }
}

function buildSeed({ plan, seed }) {
  if (seed != null) {
    return seed
  }

  const deficits = communityDeficitsFromPlan(plan)
  return [
    plan?.sceneKey ?? 'community',
    plan?.batchId ?? 'batch',
    deficits.users,
    deficits.posts,
    deficits.comments
  ].join(':')
}

function chooseWeighted(random, entries, getWeight) {
  if (entries.length === 0) {
    throw new Error('weighted selection requires at least one entry')
  }

  const weights = entries.map((entry) => Math.max(1, getWeight(entry)))
  return entries[random.weightedIndex(weights)]
}

function buildUserPool({ existingUsers, generatedUsers }) {
  return [
    ...existingUsers.map((user) => ({
      ref: createRef('existing', user.id),
      activityScore: 2 + (user.id % 5)
    })),
    ...generatedUsers.map((user, index) => ({
      ref: createRef('generated', index),
      activityScore: user.activityScore
    }))
  ]
}

export function generateCommunityPhaseDataset({ plan, existing = {}, seed } = {}) {
  const deficits = communityDeficitsFromPlan(plan)
  const normalizedExisting = normalizeExisting(existing)
  const random = createSeededRandom(buildSeed({ plan, seed }))
  const userSequenceBase = normalizedExisting.users.length

  if (deficits.users + deficits.posts + deficits.comments === 0) {
    return {
      seed: buildSeed({ plan, seed }),
      users: [],
      posts: [],
      comments: [],
      follows: [],
      likes: []
    }
  }

  if (normalizedExisting.users.length + deficits.users === 0) {
    throw new Error('phase 1 generation requires at least one available user')
  }

  if (normalizedExisting.posts.length + deficits.posts === 0 && deficits.comments > 0) {
    throw new Error('phase 1 generation requires a post source before generating comments')
  }

  const users = Array.from({ length: deficits.users }, (_, index) => {
    const activityScore = random.integer(2, 9)
    const userSequence = userSequenceBase + index + 1
    const username = `demo_${plan?.batchId ?? 'batch'}_${userSequence}_${activityScore}`

    return {
      username,
      email: `${username}@example.test`,
      password: 'mock-data-studio-password',
      salt: `salt-${plan?.batchId ?? 'batch'}-${userSequence}`,
      headerUrl: `https://static.nowcoder.com/mock/avatar-${random.pick(avatarPool)}.png`,
      score: 10 + activityScore * 7 + random.integer(0, 12),
      activityScore
    }
  })

  const userPool = buildUserPool({
    existingUsers: normalizedExisting.users,
    generatedUsers: users
  })

  const posts = Array.from({ length: deficits.posts }, (_, index) => {
    const author = chooseWeighted(random, userPool, (entry) => entry.activityScore)
    const title = `${random.pick(postTitlePrefixes)}：${random.pick(postSubjects)}${random.pick(postActions)}`
    const content =
      `${random.pick(postBodyLeadIns)}围绕 ${random.pick(postSubjects)} 做了一次可复现的数据准备，` +
      `把帖子、评论、互动关系串起来，方便演示首页、详情页和搜索结果。`

    return {
      authorRef: author.ref,
      title: `${title} #${index + 1}`,
      content,
      categorySlot: index % 3,
      score: 20 + author.activityScore * 4 + random.integer(0, 18)
    }
  })

  const postPool = [
    ...normalizedExisting.posts.map((post) => ({
      ref: createRef('existing', post.id)
    })),
    ...posts.map((post, index) => ({
      ref: createRef('generated', index),
      authorRef: post.authorRef
    }))
  ]

  const directCommentPool = normalizedExisting.comments.map((comment) => ({
    ref: createRef('existing', comment.id),
    postRef: createRef('existing', comment.postId),
    authorRef: createRef('existing', comment.userId)
  }))
  const comments = []

  for (let index = 0; index < deficits.comments; index += 1) {
    const canReply = directCommentPool.length > 0 && random.chance(0.4)
    const author = chooseWeighted(random, userPool, (entry) => entry.activityScore)

    if (canReply) {
      const parent = chooseWeighted(random, directCommentPool, () => 1)
      comments.push({
        authorRef: author.ref,
        postRef: parent.postRef,
        parentCommentRef: parent.ref,
        targetUserRef: parent.authorRef,
        content: `${random.pick(commentOpeners)}针对这条回复补一个实现细节。${random.pick(commentClosers)}`,
        depth: 2
      })
      continue
    }

    const post = chooseWeighted(random, postPool, () => 1)
    const directComment = {
      authorRef: author.ref,
      postRef: post.ref,
      parentCommentRef: null,
      targetUserRef: null,
      content: `${random.pick(commentOpeners)}先对主贴给出一个直接反馈。${random.pick(commentClosers)}`,
      depth: 1
    }
    comments.push(directComment)
    directCommentPool.push({
      ref: createRef('generated', comments.length - 1),
      postRef: post.ref,
      authorRef: author.ref
    })
  }

  const follows = []
  if (userPool.length > 1) {
    const desiredFollowCount = Math.min(userPool.length * 2, userPool.length * (userPool.length - 1))
    const seenFollowPairs = new Set(
      normalizedExisting.follows
        .filter((follow) => follow.entityType === 3)
        .map((follow) => `existing:${follow.userId}->existing:${follow.entityId}`)
    )
    let attempts = 0

    while (follows.length < desiredFollowCount && attempts < desiredFollowCount * 8) {
      attempts += 1
      const follower = chooseWeighted(random, userPool, (entry) => entry.activityScore + 2)
      const followed = chooseWeighted(random, userPool, (entry) => entry.activityScore + 1)
      const pairKey = `${follower.ref.kind}:${follower.ref.id}->${followed.ref.kind}:${followed.ref.id}`

      if (follower.ref.kind === followed.ref.kind && follower.ref.id === followed.ref.id) {
        continue
      }

      if (seenFollowPairs.has(pairKey)) {
        continue
      }

      seenFollowPairs.add(pairKey)
      follows.push({
        followerUserRef: follower.ref,
        followedUserRef: followed.ref
      })
    }
  }

  const likeTargets = [
    ...postPool.map((post) => ({
      entityType: 'posts',
      entityRef: post.ref,
      weight: 3
    })),
    ...comments.map((comment, index) => ({
      entityType: 'comments',
      entityRef: createRef('generated', index),
      weight: comment.depth === 1 ? 2 : 1
    })),
    ...normalizedExisting.comments.map((comment) => ({
      entityType: 'comments',
      entityRef: createRef('existing', comment.id),
      weight: 1
    }))
  ]
  const likes = []

  if (userPool.length > 0 && likeTargets.length > 0) {
    const desiredLikeCount = Math.min(userPool.length * 3, likeTargets.length * 2)
    const seenLikeKeys = new Set(
      normalizedExisting.likes.map(
        (like) => `existing:${like.userId}:${like.entityType}:existing:${like.entityId}`
      )
    )
    let attempts = 0

    while (likes.length < desiredLikeCount && attempts < desiredLikeCount * 10) {
      attempts += 1
      const user = chooseWeighted(random, userPool, (entry) => entry.activityScore + 1)
      const target = chooseWeighted(random, likeTargets, (entry) => entry.weight)
      const key = `${user.ref.kind}:${user.ref.id}:${target.entityType}:${target.entityRef.kind}:${target.entityRef.id}`

      if (seenLikeKeys.has(key)) {
        continue
      }

      seenLikeKeys.add(key)
      likes.push({
        userRef: user.ref,
        entityType: target.entityType,
        entityRef: target.entityRef
      })
    }
  }

  return {
    seed: buildSeed({ plan, seed }),
    users,
    posts,
    comments,
    follows,
    likes
  }
}
