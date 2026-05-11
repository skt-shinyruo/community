import { createPlanner } from '../generator/planner.mjs'

export const defaultAutoFillMetadataBatchKey = 'startup-auto-fill-default'
export const defaultAutoFillMetadataBatchType = 'startup-auto-fill'
export const contentLikeEntityTypes = new Set(['posts', 'comments'])

function emptyCommunityInsertedCounts() {
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

function emptyImInsertedCounts() {
  return {
    imRooms: 0,
    imRoomMembers: 0,
    imRoomMessages: 0,
    imConversations: 0,
    imPrivateMessages: 0
  }
}

function formatErrorMessage(error) {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return String(error)
}

function isDuplicateKeyError(error) {
  return error?.code === 'ER_DUP_ENTRY' || error?.errno === 1062 || /duplicate/i.test(formatErrorMessage(error))
}

export function createAutoFillService({
  config,
  batchRepository,
  targetRepository,
  entityRefRepository,
  planner,
  communityWriter,
  imWriter,
  communityApi,
  now = () => new Date().toISOString()
} = {}) {
  const resolvedPlanner =
    planner ??
    (targetRepository && entityRefRepository
      ? createPlanner({
          config,
          targetRepository,
          entityRefRepository
        })
      : null)

  async function ensureDefaultBatch({ requestedBy = 'mock-data-studio' } = {}) {
    if (!batchRepository?.findByBatchKey || !batchRepository?.create) {
      throw new Error('batchRepository.findByBatchKey and batchRepository.create are required')
    }

    const existingBatch = await batchRepository.findByBatchKey(defaultAutoFillMetadataBatchKey)
    if (existingBatch) {
      return existingBatch
    }

    try {
      return await batchRepository.create({
        batchKey: defaultAutoFillMetadataBatchKey,
        batchType: defaultAutoFillMetadataBatchType,
        requestedBy,
        createdAt: now()
      })
    } catch (error) {
      if (!isDuplicateKeyError(error)) {
        throw error
      }

      const duplicatedBatch = await batchRepository.findByBatchKey(defaultAutoFillMetadataBatchKey)
      if (duplicatedBatch) {
        return duplicatedBatch
      }

      throw error
    }
  }

  async function planDefaultBatch({ requestedBy = 'mock-data-studio', sceneKey } = {}) {
    const batch = await ensureDefaultBatch({ requestedBy })
    return planBatch({
      batch,
      batchId: batch.id,
      sceneKey
    })
  }

  async function planBatch({ batch = null, batchId = batch?.id ?? null, requestedBy = 'mock-data-studio', sceneKey } = {}) {
    if (!resolvedPlanner) {
      throw new Error('planner is required')
    }

    let resolvedBatch = batch
    let resolvedBatchId = batch?.id ?? batchId

    if (resolvedBatchId == null) {
      resolvedBatch = await ensureDefaultBatch({ requestedBy })
      resolvedBatchId = resolvedBatch.id
    }

    const plan = await resolvedPlanner.planDefaultBatch({
      batchId: resolvedBatchId,
      sceneKey
    })

    return {
      batch: resolvedBatch ?? { id: resolvedBatchId },
      plan
    }
  }

  async function runCompletionHooks({ generatedRefs = [] } = {}) {
    const shouldReindex = generatedRefs.some((ref) => contentLikeEntityTypes.has(ref?.entityType))

    if (!shouldReindex) {
      return {
        reindexAttempted: false,
        reindexed: false,
        skipped: true,
        warnings: []
      }
    }

    try {
      if (!communityApi?.reindexSearch) {
        throw new Error('communityApi.reindexSearch is required when content-like entities are generated')
      }

      await communityApi.reindexSearch()

      return {
        reindexAttempted: true,
        reindexed: true,
        skipped: false,
        warnings: []
      }
    } catch (error) {
      return {
        reindexAttempted: true,
        reindexed: false,
        skipped: false,
        warnings: [
          {
            code: 'search_reindex_failed',
            message: formatErrorMessage(error)
          }
        ]
      }
    }
  }

  async function writeCommunityPhase({ batchId, plan, runOptions } = {}) {
    if (!communityWriter?.writePhase) {
      throw new Error('communityWriter.writePhase is required')
    }

    return communityWriter.writePhase({
      batchId,
      plan,
      runOptions
    })
  }

  async function writeImPhase({ batchId, plan, runOptions } = {}) {
    if (!imWriter?.writePhase) {
      throw new Error('imWriter.writePhase is required')
    }

    return imWriter.writePhase({
      batchId,
      plan,
      runOptions
    })
  }

  return {
    ensureDefaultBatch,
    planBatch,
    planDefaultBatch,
    writeCommunityPhase,
    writeImPhase,
    runCompletionHooks
  }
}

export function createAutoFillJobPhases({ autoFillService } = {}) {
  if (!autoFillService?.planBatch || !autoFillService?.runCompletionHooks) {
    throw new Error('autoFillService.planBatch and autoFillService.runCompletionHooks are required')
  }

  return [
    {
      name: 'bootstrap',
      run: async ({ context, runOptions }) => {
        context.generatedRefs = []
      }
    },
    {
      name: 'plan',
      run: async ({ batch, batchId, context }) => {
        const { batch: plannedBatch, plan } = await autoFillService.planBatch({
          batch,
          batchId
        })
        context.autoFillBatch = plannedBatch
        context.autoFillPlan = plan
      }
    },
    {
      name: 'write-community',
      run: async ({ context, runOptions }) => {
        const shouldRunCommunityWriter = ['community', 'growth', 'moderation'].some((phaseName) =>
          context.autoFillPlan?.phases?.some((phase) => phase.name === phaseName && phase.needsWork)
        )

        if (!shouldRunCommunityWriter) {
          return {
            generatedRefs: [],
            insertedCounts: emptyCommunityInsertedCounts(),
            skipped: true
          }
        }

        const result = await autoFillService.writeCommunityPhase({
          batchId: context.autoFillBatch?.id,
          plan: context.autoFillPlan,
          runOptions
        })

        context.generatedRefs = [...context.generatedRefs, ...(result.generatedRefs ?? [])]
        return result
      }
    },
    {
      name: 'write-im',
      run: async ({ context, runOptions }) => {
        const imPhase = context.autoFillPlan?.phases?.find((phase) => phase.name === 'im')

        if (!imPhase?.needsWork) {
          return {
            generatedRefs: [],
            insertedCounts: emptyImInsertedCounts(),
            skipped: true
          }
        }

        const result = await autoFillService.writeImPhase({
          batchId: context.autoFillBatch?.id,
          plan: context.autoFillPlan,
          runOptions
        })

        context.generatedRefs = [...context.generatedRefs, ...(result.generatedRefs ?? [])]
        return result
      }
    },
    {
      name: 'reindex',
      run: async ({ context }) => {
        return autoFillService.runCompletionHooks({
          generatedRefs: context.generatedRefs
        })
      }
    },
    {
      name: 'finalize',
      run: async () => {}
    }
  ]
}
