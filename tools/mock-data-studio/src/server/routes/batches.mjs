import { Router } from 'express'

import {
  defaultAutoFillMetadataBatchKey
} from '../../jobs/autoFillService.mjs'
import { asyncHandler } from './asyncHandler.mjs'

function parseBatchId(value) {
  const normalized = String(value ?? '').trim()

  if (!/^\d+$/u.test(normalized)) {
    return null
  }

  return Number.parseInt(normalized, 10)
}

function sumTargetCounts(targets = []) {
  return targets.reduce((counts, target) => {
    const entityType = target?.entityType

    if (!entityType) {
      return counts
    }

    counts[entityType] = (counts[entityType] ?? 0) + Math.max(0, Number(target.targetCount) || 0)
    return counts
  }, {})
}

function countActualRefs(refs = []) {
  return refs.reduce((counts, ref) => {
    const entityType = ref?.entityType

    if (!entityType) {
      return counts
    }

    counts[entityType] = (counts[entityType] ?? 0) + 1
    return counts
  }, {})
}

function buildCountSummary(byEntityType) {
  return {
    totalCount: Object.values(byEntityType).reduce((sum, count) => sum + count, 0),
    byEntityType
  }
}

function buildFailureSummary({ batch, jobs, targetCounts, actualCounts }) {
  const byEntityType = {}

  for (const entityType of Object.keys(targetCounts)) {
    const deficit = Math.max((targetCounts[entityType] ?? 0) - (actualCounts[entityType] ?? 0), 0)

    if (deficit > 0) {
      byEntityType[entityType] = deficit
    }
  }

  const failedJobCount = jobs.filter((job) => job.status === 'failed').length
  const latestFailedJob = jobs.find((job) => job.status === 'failed') ?? null

  return {
    totalCount: Object.values(byEntityType).reduce((sum, count) => sum + count, 0),
    byEntityType,
    failedJobCount,
    hasFailures:
      batch?.status === 'failed' || failedJobCount > 0 || Object.keys(byEntityType).length > 0,
    lastErrorMessage: batch?.errorMessage ?? latestFailedJob?.errorMessage ?? null
  }
}

function isDefaultBatch(batch) {
  return batch?.batchKey === defaultAutoFillMetadataBatchKey
}

function isNonTerminalJob(job) {
  return job?.status === 'pending' || job?.status === 'running'
}

function buildHistorySummary({ defaultEntry, manualEntries }) {
  return {
    totalBatchCount: (defaultEntry ? 1 : 0) + manualEntries.length,
    defaultBatchId: defaultEntry?.batch?.id ?? null,
    manualBatchCount: manualEntries.length,
    hasManualBatches: manualEntries.length > 0
  }
}

function buildDetailMetadata(entry) {
  return {
    batchId: entry.batch.id,
    isDefaultBatch: isDefaultBatch(entry.batch),
    jobCount: entry.jobs?.length ?? 0,
    latestJobId: entry.latestJob?.id ?? null,
    canDelete: !isDefaultBatch(entry.batch) && !isNonTerminalJob(entry.latestJob),
    lastErrorMessage: entry.failureSummary.lastErrorMessage
  }
}

async function buildBatchEntry(batch, { jobRepository, targetRepository, entityRefRepository, includeJobs = false }) {
  const [jobs, targets, refs] = await Promise.all([
    jobRepository.listByBatchId(batch.id),
    targetRepository.listByBatchId(batch.id),
    entityRefRepository.listByBatchId(batch.id)
  ])
  const targetCounts = sumTargetCounts(targets)
  const actualCounts = countActualRefs(refs)
  const entry = {
    batch,
    latestJob: jobs[0] ?? null,
    targetSummary: buildCountSummary(targetCounts),
    actualSummary: buildCountSummary(actualCounts),
    failureSummary: buildFailureSummary({
      batch,
      jobs,
      targetCounts,
      actualCounts
    })
  }

  if (includeJobs) {
    entry.jobs = jobs
  }

  return entry
}

export function buildBatchesRouter({
  batchRepository,
  jobRepository,
  targetRepository,
  entityRefRepository,
  deleteBatchService
} = {}) {
  if (!batchRepository?.listAll || !batchRepository?.findById) {
    throw new Error('batchRepository.listAll and batchRepository.findById are required')
  }

  if (!jobRepository?.listByBatchId) {
    throw new Error('jobRepository.listByBatchId is required')
  }

  if (!targetRepository?.listByBatchId) {
    throw new Error('targetRepository.listByBatchId is required')
  }

  if (!entityRefRepository?.listByBatchId) {
    throw new Error('entityRefRepository.listByBatchId is required')
  }

  if (!deleteBatchService?.deleteBatch) {
    throw new Error('deleteBatchService.deleteBatch is required')
  }

  const router = Router()

  router.get('/', asyncHandler(async (_req, res) => {
    const batches = await batchRepository.listAll()
    const entries = await Promise.all(
      batches.map((batch) =>
        buildBatchEntry(batch, {
          jobRepository,
          targetRepository,
          entityRefRepository
        })
      )
    )
    const defaultEntry = entries.find((entry) => isDefaultBatch(entry.batch)) ?? null
    const manualEntries = entries.filter((entry) => !isDefaultBatch(entry.batch))

    res.json({
      ok: true,
      defaultBatch: defaultEntry,
      manualBatches: manualEntries,
      history: buildHistorySummary({
        defaultEntry,
        manualEntries
      })
    })
  }))

  router.get('/:batchId', asyncHandler(async (req, res) => {
    const batchId = parseBatchId(req.params.batchId)

    if (batchId == null) {
      res.status(400).json({
        ok: false,
        error: 'invalid_batch_id'
      })
      return
    }

    const batch = await batchRepository.findById(batchId)

    if (!batch) {
      res.status(404).json({
        ok: false,
        error: 'batch_not_found'
      })
      return
    }

    const entry = await buildBatchEntry(batch, {
      jobRepository,
      targetRepository,
      entityRefRepository,
      includeJobs: true
    })

    res.json({
      ok: true,
      batch: entry.batch,
      latestJob: entry.latestJob,
      jobs: entry.jobs,
      targetSummary: entry.targetSummary,
      actualSummary: entry.actualSummary,
      failureSummary: entry.failureSummary,
      detail: buildDetailMetadata(entry)
    })
  }))

  router.delete('/:batchId', asyncHandler(async (req, res) => {
    const batchId = parseBatchId(req.params.batchId)

    if (batchId == null) {
      res.status(400).json({
        ok: false,
        error: 'invalid_batch_id'
      })
      return
    }

    try {
      const result = await deleteBatchService.deleteBatch(batchId)
      res.json({
        ok: true,
        ...result
      })
    } catch (error) {
      if (error?.code === 'BATCH_NOT_FOUND') {
        res.status(404).json({
          ok: false,
          error: 'batch_not_found'
        })
        return
      }

      if (error?.code === 'BATCH_JOB_RUNNING') {
        res.status(409).json({
          ok: false,
          error: 'batch_job_running',
          runningJob: error.runningJob ?? null
        })
        return
      }

      throw error
    }
  }))

  return router
}
