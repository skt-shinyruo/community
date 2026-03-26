function createMissingBatchError(batchId) {
  const error = new Error(`Missing demo_batch row ${batchId}`)
  error.code = 'BATCH_NOT_FOUND'
  error.status = 404
  error.batchId = batchId
  return error
}

function createRunningJobError(batchId, runningJob) {
  const error = new Error(`Batch ${batchId} still has a running job`)
  error.code = 'BATCH_JOB_RUNNING'
  error.status = 409
  error.batchId = batchId
  error.runningJob = runningJob
  return error
}

function createUnsupportedEntityTypeError(entityType) {
  const error = new Error(`Unsupported batch entity type: ${entityType}`)
  error.code = 'UNSUPPORTED_BATCH_ENTITY_TYPE'
  error.status = 400
  error.entityType = entityType
  return error
}

const NONTERMINAL_JOB_STATUSES = new Set(['pending', 'running'])

function parseScalarId(entityKey, entityType) {
  const normalized = String(entityKey ?? '').trim()

  if (!/^\d+$/u.test(normalized)) {
    throw new Error(`Invalid ${entityType} entity key: ${entityKey}`)
  }

  return Number.parseInt(normalized, 10)
}

function parseCompositeKey(entityKey, entityType) {
  const normalized = String(entityKey ?? '').trim()
  const match = normalized.match(/^(\d+):(\d+):(\d+)$/u)

  if (!match) {
    throw new Error(`Invalid ${entityType} entity key: ${entityKey}`)
  }

  return {
    userId: Number.parseInt(match[1], 10),
    targetEntityType: Number.parseInt(match[2], 10),
    entityId: Number.parseInt(match[3], 10)
  }
}

function parsePairKey(entityKey, entityType) {
  const normalized = String(entityKey ?? '').trim()
  const match = normalized.match(/^(\d+):(\d+)$/u)

  if (!match) {
    throw new Error(`Invalid ${entityType} entity key: ${entityKey}`)
  }

  return {
    left: Number.parseInt(match[1], 10),
    right: Number.parseInt(match[2], 10)
  }
}

function parseConversationSeqKey(entityKey, entityType) {
  const normalized = String(entityKey ?? '').trim()
  const separatorIndex = normalized.lastIndexOf(':')

  if (separatorIndex <= 0 || separatorIndex === normalized.length - 1) {
    throw new Error(`Invalid ${entityType} entity key: ${entityKey}`)
  }

  const conversationId = normalized.slice(0, separatorIndex)
  const seq = parseScalarId(normalized.slice(separatorIndex + 1), entityType)

  return {
    conversationId,
    seq
  }
}

const COMMUNITY_DELETE_ORDER = [
  {
    entityType: 'notices',
    countKey: 'notices',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from message where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'messages',
    countKey: 'messages',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from message where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'moderation_actions',
    countKey: 'moderationActions',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from moderation_action where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'reports',
    countKey: 'reports',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from report where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'reward_orders',
    countKey: 'rewardOrders',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from reward_order where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'reward_items',
    countKey: 'rewardItems',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from reward_item where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'reward_grant_records',
    countKey: 'rewardGrantRecords',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from reward_grant_record where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'reward_ledgers',
    countKey: 'rewardLedgers',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from reward_ledger where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'user_task_progress',
    countKey: 'userTaskProgress',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from user_task_progress where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'growth_check_ins',
    countKey: 'growthCheckIns',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from growth_check_in where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'reward_accounts',
    countKey: 'rewardAccounts',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from reward_account where user_id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'social_likes',
    countKey: 'socialLikes',
    deleteRef: async (db, ref) => {
      const key = parseCompositeKey(ref.entityKey, ref.entityType)
      return db.execute(
        `delete from social_like where user_id = ? and entity_type = ? and entity_id = ?`,
        [key.userId, key.targetEntityType, key.entityId]
      )
    }
  },
  {
    entityType: 'social_follows',
    countKey: 'socialFollows',
    deleteRef: async (db, ref) => {
      const key = parseCompositeKey(ref.entityKey, ref.entityType)
      return db.execute(
        `delete from social_follow where user_id = ? and entity_type = ? and entity_id = ?`,
        [key.userId, key.targetEntityType, key.entityId]
      )
    }
  },
  {
    entityType: 'comments',
    countKey: 'comments',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from comment where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'posts',
    countKey: 'posts',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from discuss_post where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  },
  {
    entityType: 'users',
    countKey: 'users',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from user where id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  }
]

const IM_CORE_DELETE_ORDER = [
  {
    entityType: 'im_private_messages',
    countKey: 'imPrivateMessages',
    deleteRef: async (db, ref) => {
      const key = parseConversationSeqKey(ref.entityKey, ref.entityType)
      return db.execute(`delete from im_core.im_private_message where conversation_id = ? and seq = ?`, [
        key.conversationId,
        key.seq
      ])
    }
  },
  {
    entityType: 'im_conversations',
    countKey: 'imConversations',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from im_core.im_conversation where conversation_id = ?`, [String(ref.entityKey)])
    }
  },
  {
    entityType: 'im_room_messages',
    countKey: 'imRoomMessages',
    deleteRef: async (db, ref) => {
      const key = parsePairKey(ref.entityKey, ref.entityType)
      return db.execute(`delete from im_core.im_room_message where room_id = ? and seq = ?`, [key.left, key.right])
    }
  },
  {
    entityType: 'im_room_members',
    countKey: 'imRoomMembers',
    deleteRef: async (db, ref) => {
      const key = parsePairKey(ref.entityKey, ref.entityType)
      return db.execute(`delete from im_core.im_room_member where room_id = ? and user_id = ?`, [key.left, key.right])
    }
  },
  {
    entityType: 'im_rooms',
    countKey: 'imRooms',
    deleteRef: async (db, ref) => {
      return db.execute(`delete from im_core.im_room where room_id = ?`, [parseScalarId(ref.entityKey, ref.entityType)])
    }
  }
]

const DELETE_STEPS = [...COMMUNITY_DELETE_ORDER, ...IM_CORE_DELETE_ORDER]
const DELETE_STEP_BY_ENTITY_TYPE = new Map(DELETE_STEPS.map((step, index) => [step.entityType, { ...step, index }]))

function orderRefsForDeletion(refs) {
  return [...refs].sort((left, right) => {
    const leftStep = DELETE_STEP_BY_ENTITY_TYPE.get(left.entityType)
    const rightStep = DELETE_STEP_BY_ENTITY_TYPE.get(right.entityType)

    if (!leftStep) {
      throw createUnsupportedEntityTypeError(left.entityType)
    }

    if (!rightStep) {
      throw createUnsupportedEntityTypeError(right.entityType)
    }

    return leftStep.index - rightStep.index
  })
}

function createEmptyDeletedCounts() {
  return {
    business: {
      notices: 0,
      messages: 0,
      reports: 0,
      moderationActions: 0,
      growthCheckIns: 0,
      userTaskProgress: 0,
      rewardAccounts: 0,
      rewardLedgers: 0,
      rewardGrantRecords: 0,
      rewardItems: 0,
      rewardOrders: 0,
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
    },
    metadata: {
      entityRefs: 0,
      targets: 0,
      jobs: 0,
      batches: 0
    }
  }
}

async function loadCommentRecord(db, commentId) {
  const rows = await db.query(
    `select id, entity_type, entity_id
       from comment
      where id = ?`,
    [commentId]
  )

  return rows[0] ?? null
}

async function resolveCommentPostId(db, commentId) {
  let comment = await loadCommentRecord(db, commentId)

  while (comment) {
    if (Number(comment.entity_type) === 1) {
      return Number(comment.entity_id)
    }

    if (Number(comment.entity_type) !== 2) {
      return null
    }

    comment = await loadCommentRecord(db, Number(comment.entity_id))
  }

  return null
}

async function countVisibleCommentsForPost(db, postId) {
  const rows = await db.query(
    `select count(*) as comment_count
       from comment
      where status = 0
        and (
          entity_type = 1 and entity_id = ?
          or entity_type = 2 and entity_id in (
            select id
              from comment
             where status = 0 and entity_type = 1 and entity_id = ?
          )
        )`,
    [postId, postId]
  )

  return Number(rows[0]?.comment_count ?? 0)
}

export function createDeleteBatchService({
  db,
  batchRepository,
  jobRepository,
  entityRefRepository
} = {}) {
  if (!db?.execute || !db?.query) {
    throw new Error('db.execute and db.query are required')
  }

  if (!batchRepository?.findById && !batchRepository?.getById) {
    throw new Error('batchRepository.findById or batchRepository.getById is required')
  }

  if (!jobRepository?.listByBatchId) {
    throw new Error('jobRepository.listByBatchId is required')
  }

  if (!entityRefRepository?.listByBatchId) {
    throw new Error('entityRefRepository.listByBatchId is required')
  }

  const findBatchById = batchRepository.findById
    ? (batchId) => batchRepository.findById(batchId)
    : async (batchId) => {
        try {
          return await batchRepository.getById(batchId)
        } catch (error) {
          if (error?.code === 'BATCH_NOT_FOUND' || /Missing demo_batch row/u.test(error?.message ?? '')) {
            return null
          }

          throw error
        }
      }

  const runInTransaction = db.withTransaction ? (work) => db.withTransaction(work) : (work) => work(db)

  return {
    async deleteBatch(batchId) {
      const batch = await findBatchById(batchId)

      if (!batch) {
        throw createMissingBatchError(batchId)
      }

      const jobs = await jobRepository.listByBatchId(batchId)
      const runningJob = jobs.find((job) => NONTERMINAL_JOB_STATUSES.has(job.status))

      if (runningJob) {
        throw createRunningJobError(batchId, runningJob)
      }

      const refs = orderRefsForDeletion(await entityRefRepository.listByBatchId(batchId))
      const deleted = createEmptyDeletedCounts()

      await runInTransaction(async (txDb) => {
        const survivingPostIdsNeedingCountRepair = new Set()

        for (const ref of refs) {
          if (ref.entityType === 'comments') {
            const postId = await resolveCommentPostId(txDb, parseScalarId(ref.entityKey, ref.entityType))

            if (postId != null) {
              survivingPostIdsNeedingCountRepair.add(postId)
            }
          }

          const step = DELETE_STEP_BY_ENTITY_TYPE.get(ref.entityType)
          const result = await step.deleteRef(txDb, ref)
          deleted.business[step.countKey] += Number(result?.affectedRows ?? 0)
        }

        for (const postId of survivingPostIdsNeedingCountRepair) {
          const commentCount = await countVisibleCommentsForPost(txDb, postId)
          await txDb.execute(`update discuss_post set comment_count = ? where id = ?`, [commentCount, postId])
        }

        deleted.metadata.entityRefs = Number(
          (await txDb.execute(`delete from demo_entity_ref where batch_id = ?`, [batchId]))?.affectedRows ?? 0
        )
        deleted.metadata.targets = Number(
          (await txDb.execute(`delete from demo_batch_target where batch_id = ?`, [batchId]))?.affectedRows ?? 0
        )
        deleted.metadata.jobs = Number(
          (await txDb.execute(`delete from demo_job where batch_id = ?`, [batchId]))?.affectedRows ?? 0
        )
        deleted.metadata.batches = Number(
          (await txDb.execute(`delete from demo_batch where id = ?`, [batchId]))?.affectedRows ?? 0
        )
      })

      return {
        batchId,
        deleted
      }
    }
  }
}
