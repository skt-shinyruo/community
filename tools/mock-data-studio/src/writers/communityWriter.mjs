import { formatMysqlTimestamp } from '../db/mysql.mjs'
import { generateCommunityPhaseDataset } from '../generator/contentGenerator.mjs'
import { generateDomainPhaseDataset } from '../generator/domainGenerator.mjs'

const ENTITY_TYPE_POST = 1
const ENTITY_TYPE_COMMENT = 2
const ENTITY_TYPE_USER = 3
const ACTIVE_USER_STATUS = 1

function normalizeCount(value) {
  return Math.max(0, Number.parseInt(value, 10) || 0)
}

function buildCommunityDeficits(plan) {
  const communityPhase = plan?.phases?.find((phase) => phase.name === 'community')
  return {
    users: normalizeCount(communityPhase?.deficits?.users ?? plan?.deficits?.users ?? 0),
    posts: normalizeCount(communityPhase?.deficits?.posts ?? plan?.deficits?.posts ?? 0),
    comments: normalizeCount(communityPhase?.deficits?.comments ?? plan?.deficits?.comments ?? 0)
  }
}

function buildEmptyInsertedCounts() {
  return {
    users: 0,
    posts: 0,
    comments: 0,
    socialFollows: 0,
    socialLikes: 0,
    reports: 0,
    moderationActions: 0,
    userTaskProgress: 0
  }
}

function resolveRunDb(db, txDb) {
  return txDb ?? db
}

function expandInsertedIds(result, rowCount) {
  if (rowCount === 0) {
    return []
  }

  const firstId = Number(result?.insertId)
  if (!Number.isInteger(firstId) || firstId < 1) {
    throw new Error('bulk insert did not return a valid insertId')
  }

  return Array.from({ length: rowCount }, (_, index) => firstId + index)
}

function formatBulkInsert(tableName, columns, rowCount) {
  const valueGroup = `(${columns.map(() => '?').join(', ')})`
  return `insert into ${tableName} (${columns.join(', ')}) values ${Array.from({ length: rowCount }, () => valueGroup).join(', ')}`
}

function createGeneratedRef(entityType, entityKey, createdAt) {
  return {
    entityType,
    entityKey: String(entityKey),
    createdAt
  }
}

async function appendEntityRefs({ db, entityRefRepository, batchId, refs, txDb = null }) {
  if (refs.length === 0) {
    return []
  }

  if (entityRefRepository?.appendForBatch) {
    await entityRefRepository.appendForBatch(batchId, refs, {
      txDb: resolveRunDb(db, txDb)
    })
    return refs
  }

  const runDb = resolveRunDb(db, txDb)
  for (const ref of refs) {
    await runDb.execute(
      `insert into demo_entity_ref (
        batch_id,
        entity_type,
        entity_key,
        created_at
      ) values (?, ?, ?, ?)`,
      [batchId, ref.entityType, ref.entityKey, formatMysqlTimestamp(ref.createdAt, 'demo_entity_ref.createdAt')]
    )
  }

  return refs
}

function resolveId(ref, ids) {
  if (ref.kind === 'existing') {
    return ref.id
  }

  if (ref.kind === 'generated') {
    return ids[ref.id]
  }

  throw new Error(`Unsupported generated ref kind: ${ref.kind}`)
}

async function loadExistingState(runDb) {
  const [users, posts, comments, categories, follows, likes] = await Promise.all([
    runDb.query(`select id from user order by id asc`),
    runDb.query(`select id, comment_count from discuss_post order by id asc`),
    runDb.query(
      `select id, entity_id as post_id, user_id
         from comment
        where status = 0 and entity_type = 1
        order by id asc`
    ),
    runDb.query(`select id, name from category order by id asc`),
    runDb.query(`select user_id, entity_type, entity_id from social_follow order by user_id asc, entity_id asc`),
    runDb.query(`select user_id, entity_type, entity_id from social_like order by user_id asc, entity_type asc, entity_id asc`)
  ])

  return {
    users: users.map((user) => ({ id: Number(user.id) })),
    posts: posts.map((post) => ({
      id: Number(post.id),
      commentCount: normalizeCount(post.comment_count)
    })),
    comments: comments.map((comment) => ({
      id: Number(comment.id),
      postId: Number(comment.post_id),
      userId: Number(comment.user_id)
    })),
    categories: categories.map((category) => ({
      id: Number(category.id),
      name: category.name
    })),
    follows: follows.map((follow) => ({
      userId: Number(follow.user_id),
      entityType: Number(follow.entity_type),
      entityId: Number(follow.entity_id)
    })),
    likes: likes
      .filter((like) => Number(like.entity_type) === ENTITY_TYPE_POST || Number(like.entity_type) === ENTITY_TYPE_COMMENT)
      .map((like) => ({
        userId: Number(like.user_id),
        entityType: Number(like.entity_type) === ENTITY_TYPE_POST ? 'posts' : 'comments',
        entityId: Number(like.entity_id)
      }))
  }
}

async function loadExistingDomainState(runDb, { batchRefs = [] } = {}) {
  const batchReportIds = new Set(
    batchRefs
      .filter((ref) => ref.entityType === 'reports' && /^\d+$/u.test(String(ref.entityKey ?? '')))
      .map((ref) => Number.parseInt(String(ref.entityKey), 10))
  )

  const [reports, userTaskProgress] = await Promise.all([
    runDb.query(`select id, reporter_id, target_type, target_id from report order by id asc`),
    runDb.query(`select user_id, task_code, period_key from user_task_progress order by user_id asc, task_code asc, period_key asc`)
  ])

  return {
    reports: reports.map((report) => ({
      id: Number(report.id),
      reporterId: Number(report.reporter_id),
      targetType: Number(report.target_type),
      targetId: Number(report.target_id)
    })),
    batchReports: reports
      .filter((report) => batchReportIds.has(Number(report.id)))
      .map((report) => ({
        id: Number(report.id)
      })),
    userTaskProgress: userTaskProgress.map((entry) => ({
      userId: Number(entry.user_id),
      taskCode: entry.task_code,
      periodKey: entry.period_key
    }))
  }
}

function buildTimestampSource(now) {
  return () => {
    const timestamp = now()
    return {
      iso: timestamp,
      mysql: formatMysqlTimestamp(timestamp, 'communityWriter.timestamp')
    }
  }
}

function toInsertParams(rows) {
  return rows.flatMap((row) => row)
}

function resolveOptionalIsoTimestamp(value, label) {
  return value == null ? null : formatMysqlTimestamp(value, label)
}

export function createCommunityWriter({
  db,
  entityRefRepository = null,
  aiContentEnhancer = null,
  now = () => new Date().toISOString()
} = {}) {
  if (!db?.query || !db?.execute) {
    throw new Error('db.query and db.execute are required')
  }

  const nextTimestamp = buildTimestampSource(now)

  return {
    async writePhase({ batchId, plan, runOptions = null } = {}) {
      if (batchId == null) {
        throw new Error('batchId is required')
      }

      if (!plan) {
        throw new Error('plan is required')
      }

      const deficits = buildCommunityDeficits(plan)
      const insertedCounts = buildEmptyInsertedCounts()

      const nonImPhaseNeedsWork = ['community', 'growth', 'moderation'].some((phaseName) =>
        plan?.phases?.some((phase) => phase.name === phaseName && phase.needsWork)
      )

      if (!nonImPhaseNeedsWork && deficits.users + deficits.posts + deficits.comments === 0) {
        return {
          insertedCounts,
          generatedRefs: []
        }
      }

      const runInTransaction = db.withTransaction ? (work) => db.withTransaction(work) : (work) => work(db)

      return runInTransaction(async (txDb) => {
        const runDb = resolveRunDb(db, txDb)
        const existing = await loadExistingState(runDb)
        const batchRefs = entityRefRepository?.listByBatchId ? await entityRefRepository.listByBatchId(batchId) : []
        const existingDomain = await loadExistingDomainState(runDb, {
          batchRefs
        })
        const dataset = generateCommunityPhaseDataset({
          plan,
          existing: {
            users: existing.users,
            posts: existing.posts,
            comments: existing.comments,
            follows: existing.follows,
            likes: existing.likes
          }
        })

        const applyTextEnhancement = async (items, fieldName, kind) => {
          if (!aiContentEnhancer?.enhanceTextsForRun || items.length === 0) {
            return
          }

          const originalValues = items.map((item) => item?.[fieldName] ?? '')
          const enhanced = await aiContentEnhancer.enhanceTextsForRun({
            kind,
            inputs: originalValues,
            runOptions
          })

          if (!Array.isArray(enhanced?.outputs) || enhanced.outputs.length !== items.length) {
            return
          }

          items.forEach((item, index) => {
            item[fieldName] = enhanced.outputs[index]
          })
        }

        await applyTextEnhancement(dataset.posts, 'title', 'post-title')
        await applyTextEnhancement(dataset.posts, 'content', 'post-content')
        await applyTextEnhancement(dataset.comments, 'content', 'comment-content')

        const generatedRefs = []

        const userRows = dataset.users.map((user) => {
          const timestamp = nextTimestamp()
          return [
            user.username,
            user.password,
            user.salt,
            user.email,
            0,
            ACTIVE_USER_STATUS,
            user.headerUrl,
            timestamp.mysql
          ]
        })
        const insertedUserIds =
          userRows.length === 0
            ? []
            : expandInsertedIds(
                await runDb.execute(
                  formatBulkInsert(
                    'user',
                    ['username', 'password', 'salt', 'email', 'type', 'status', 'header_url', 'create_time'],
                    userRows.length
                  ),
                  toInsertParams(userRows)
                ),
                userRows.length
              )
        for (const insertedUserId of insertedUserIds) {
          generatedRefs.push(createGeneratedRef('users', insertedUserId, nextTimestamp().iso))
        }

        const categoryIds = existing.categories.map((category) => category.id)
        const postRows = dataset.posts.map((post) => {
          const timestamp = nextTimestamp()
          return [
            resolveId(post.authorRef, insertedUserIds),
            categoryIds.length === 0 ? null : categoryIds[post.categorySlot % categoryIds.length],
            post.title,
            post.content,
            0,
            0,
            timestamp.mysql,
            0,
            post.score
          ]
        })
        const insertedPostIds =
          postRows.length === 0
            ? []
            : expandInsertedIds(
                await runDb.execute(
                  formatBulkInsert(
                    'discuss_post',
                    ['user_id', 'category_id', 'title', 'content', 'type', 'status', 'create_time', 'comment_count', 'score'],
                    postRows.length
                  ),
                  toInsertParams(postRows)
                ),
                postRows.length
              )
        for (const insertedPostId of insertedPostIds) {
          generatedRefs.push(createGeneratedRef('posts', insertedPostId, nextTimestamp().iso))
        }

        const directComments = []
        const replyComments = []
        dataset.comments.forEach((comment, index) => {
          if (comment.parentCommentRef) {
            replyComments.push({ ...comment, originalIndex: index })
            return
          }

          directComments.push({ ...comment, originalIndex: index })
        })

        const directCommentRows = directComments.map((comment) => {
          const timestamp = nextTimestamp()
          return [
            resolveId(comment.authorRef, insertedUserIds),
            ENTITY_TYPE_POST,
            resolveId(comment.postRef, insertedPostIds),
            0,
            comment.content,
            0,
            timestamp.mysql
          ]
        })
        const insertedDirectCommentIds =
          directCommentRows.length === 0
            ? []
            : expandInsertedIds(
                await runDb.execute(
                  formatBulkInsert(
                    'comment',
                    ['user_id', 'entity_type', 'entity_id', 'target_id', 'content', 'status', 'create_time'],
                    directCommentRows.length
                  ),
                  toInsertParams(directCommentRows)
                ),
                directCommentRows.length
              )

        const commentIdByGeneratedIndex = new Map()
        directComments.forEach((comment, index) => {
          commentIdByGeneratedIndex.set(comment.originalIndex, insertedDirectCommentIds[index])
        })

        const replyCommentRows = replyComments.map((comment) => {
          const timestamp = nextTimestamp()
          return [
            resolveId(comment.authorRef, insertedUserIds),
            ENTITY_TYPE_COMMENT,
            resolveId(comment.parentCommentRef, Object.fromEntries(commentIdByGeneratedIndex)),
            comment.targetUserRef ? resolveId(comment.targetUserRef, insertedUserIds) : 0,
            comment.content,
            0,
            timestamp.mysql
          ]
        })
        const insertedReplyCommentIds =
          replyCommentRows.length === 0
            ? []
            : expandInsertedIds(
                await runDb.execute(
                  formatBulkInsert(
                    'comment',
                    ['user_id', 'entity_type', 'entity_id', 'target_id', 'content', 'status', 'create_time'],
                    replyCommentRows.length
                  ),
                  toInsertParams(replyCommentRows)
                ),
                replyCommentRows.length
              )

        replyComments.forEach((comment, index) => {
          commentIdByGeneratedIndex.set(comment.originalIndex, insertedReplyCommentIds[index])
        })

        const insertedCommentIds = Array.from({ length: dataset.comments.length }, (_, index) => {
          const insertedCommentId = commentIdByGeneratedIndex.get(index)
          if (!insertedCommentId) {
            throw new Error(`Missing inserted comment id for generated comment ${index}`)
          }

          return insertedCommentId
        })
        for (const insertedCommentId of [...insertedDirectCommentIds, ...insertedReplyCommentIds]) {
          generatedRefs.push(createGeneratedRef('comments', insertedCommentId, nextTimestamp().iso))
        }

        const followRows = dataset.follows.map((follow) => {
          const timestamp = nextTimestamp()
          return [
            resolveId(follow.followerUserRef, insertedUserIds),
            ENTITY_TYPE_USER,
            resolveId(follow.followedUserRef, insertedUserIds),
            timestamp.mysql
          ]
        })
        if (followRows.length > 0) {
          await runDb.execute(
            formatBulkInsert('social_follow', ['user_id', 'entity_type', 'entity_id', 'created_at'], followRows.length),
            toInsertParams(followRows)
          )
        }
        followRows.forEach(([userId, entityType, entityId]) => {
          generatedRefs.push(createGeneratedRef('social_follows', `${userId}:${entityType}:${entityId}`, nextTimestamp().iso))
        })

        const likeRows = dataset.likes.map((like) => {
          const timestamp = nextTimestamp()
          return [
            resolveId(like.userRef, insertedUserIds),
            like.entityType === 'posts' ? ENTITY_TYPE_POST : ENTITY_TYPE_COMMENT,
            resolveId(like.entityRef, like.entityType === 'posts' ? insertedPostIds : insertedCommentIds),
            timestamp.mysql
          ]
        })
        if (likeRows.length > 0) {
          await runDb.execute(
            formatBulkInsert('social_like', ['user_id', 'entity_type', 'entity_id', 'created_at'], likeRows.length),
            toInsertParams(likeRows)
          )
        }
        likeRows.forEach(([userId, entityType, entityId]) => {
          generatedRefs.push(createGeneratedRef('social_likes', `${userId}:${entityType}:${entityId}`, nextTimestamp().iso))
        })

        const newCommentCountsByPostId = dataset.comments.reduce((counts, comment) => {
          const postId = resolveId(comment.postRef, insertedPostIds)
          counts.set(postId, (counts.get(postId) ?? 0) + 1)
          return counts
        }, new Map())
        const existingPostCommentCounts = new Map(existing.posts.map((post) => [post.id, post.commentCount]))
        for (const postId of insertedPostIds) {
          existingPostCommentCounts.set(postId, 0)
        }
        for (const [postId, newCommentCount] of newCommentCountsByPostId.entries()) {
          await runDb.execute(`update discuss_post set comment_count = ? where id = ?`, [
            (existingPostCommentCounts.get(postId) ?? 0) + newCommentCount,
            postId
          ])
        }

        await appendEntityRefs({
          db,
          entityRefRepository,
          batchId,
          refs: generatedRefs,
          txDb: runDb
        })

        insertedCounts.users = insertedUserIds.length
        insertedCounts.posts = insertedPostIds.length
        insertedCounts.comments = insertedCommentIds.length
        insertedCounts.socialFollows = followRows.length
        insertedCounts.socialLikes = likeRows.length

        const domainDataset = generateDomainPhaseDataset({
          plan,
          existing: {
            users: [...existing.users, ...insertedUserIds.map((id) => ({ id }))],
            posts: [...existing.posts, ...insertedPostIds.map((id) => ({ id }))],
            comments: [
              ...existing.comments,
              ...dataset.comments.map((comment, index) => ({
                id: insertedCommentIds[index],
                postId: resolveId(comment.postRef, insertedPostIds),
                userId: resolveId(comment.authorRef, insertedUserIds)
              }))
            ],
            reports: existingDomain.reports,
            batchReports: existingDomain.batchReports,
            userTaskProgress: existingDomain.userTaskProgress
          }
        })

        await applyTextEnhancement(domainDataset.reports, 'detail', 'report-detail')
        await applyTextEnhancement(domainDataset.moderationActions, 'reason', 'moderation-reason')

        const reportRows = domainDataset.reports.map((report) => {
          const timestamp = nextTimestamp()
          return [report.reporterId, report.targetType, report.targetId, report.reason, report.detail, report.status, timestamp.mysql]
        })
        const insertedReportIds =
          reportRows.length === 0
            ? []
            : expandInsertedIds(
                await runDb.execute(
                  formatBulkInsert('report', ['reporter_id', 'target_type', 'target_id', 'reason', 'detail', 'status', 'create_time'], reportRows.length),
                  toInsertParams(reportRows)
                ),
                reportRows.length
              )
        for (const insertedReportId of insertedReportIds) {
          generatedRefs.push(createGeneratedRef('reports', insertedReportId, nextTimestamp().iso))
        }
        insertedCounts.reports = insertedReportIds.length

        const moderationActionRows = domainDataset.moderationActions.map((action) => {
          const timestamp = nextTimestamp()
          return [
            action.reportRef ? resolveId(action.reportRef, insertedReportIds) : null,
            action.actorId,
            action.action,
            action.reason,
            action.durationSeconds,
            timestamp.mysql
          ]
        })
        const insertedModerationActionIds =
          moderationActionRows.length === 0
            ? []
            : expandInsertedIds(
                await runDb.execute(
                  formatBulkInsert(
                    'moderation_action',
                    ['report_id', 'actor_id', 'action', 'reason', 'duration_seconds', 'create_time'],
                    moderationActionRows.length
                  ),
                  toInsertParams(moderationActionRows)
                ),
                moderationActionRows.length
              )
        for (const insertedModerationActionId of insertedModerationActionIds) {
          generatedRefs.push(createGeneratedRef('moderation_actions', insertedModerationActionId, nextTimestamp().iso))
        }
        insertedCounts.moderationActions = insertedModerationActionIds.length

        const taskProgressRows = domainDataset.userTaskProgress.map((progress) => {
          const timestamp = nextTimestamp()
          return [
            progress.userId,
            progress.taskCode,
            progress.periodKey,
            progress.currentValue,
            progress.targetValue,
            progress.status,
            resolveOptionalIsoTimestamp(progress.reachedAt, 'communityWriter.userTaskProgress.reachedAt'),
            resolveOptionalIsoTimestamp(progress.claimedAt, 'communityWriter.userTaskProgress.claimedAt'),
            progress.rewardGrantId,
            progress.lastSourceEventId,
            timestamp.mysql
          ]
        })
        const insertedTaskProgressIds =
          taskProgressRows.length === 0
            ? []
            : expandInsertedIds(
                await runDb.execute(
                  formatBulkInsert(
                    'user_task_progress',
                    [
                      'user_id',
                      'task_code',
                      'period_key',
                      'current_value',
                      'target_value',
                      'status',
                      'reached_at',
                      'claimed_at',
                      'reward_grant_id',
                      'last_source_event_id',
                      'update_time'
                    ],
                    taskProgressRows.length
                  ),
                  toInsertParams(taskProgressRows)
                ),
                taskProgressRows.length
              )
        for (const insertedTaskProgressId of insertedTaskProgressIds) {
          generatedRefs.push(createGeneratedRef('user_task_progress', insertedTaskProgressId, nextTimestamp().iso))
        }
        insertedCounts.userTaskProgress = insertedTaskProgressIds.length

        await appendEntityRefs({
          db,
          entityRefRepository,
          batchId,
          refs: generatedRefs.slice(insertedUserIds.length + insertedPostIds.length + insertedCommentIds.length + followRows.length + likeRows.length),
          txDb: runDb
        })

        return {
          insertedCounts,
          generatedRefs
        }
      })
    }
  }
}
