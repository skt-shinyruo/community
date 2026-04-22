import { Router } from 'express'

import { normalizeUuid } from '../../db/uuidv7.mjs'
import { asyncHandler } from './asyncHandler.mjs'

function formatErrorMessage(error) {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return String(error)
}

function parseJobId(value) {
  try {
    return normalizeUuid(value)
  } catch {
    return null
  }
}

function normalizeJobRequest(body = {}) {
  const normalizedMode =
    body.mode === 'auto-fill' || body.batchType === 'startup-auto-fill'
      ? 'auto-fill'
      : 'manual-generate'
  const aiEnhancement = normalizedMode === 'manual-generate' && Boolean(body.aiEnhancement)

  return {
    requestedBy: body.requestedBy ?? 'mock-data-studio',
    batchType: body.batchType ?? 'demo-seed',
    jobType: body.jobType ?? 'demo-seed',
    mode: normalizedMode,
    aiEnhancement
  }
}

function buildAiNotReadyMessage(config) {
  const missingConfig = Array.isArray(config?.ai?.missingConfig) ? config.ai.missingConfig : []

  if (missingConfig.length === 0) {
    return 'AI enhancement is not ready'
  }

  return `AI enhancement is not ready: missing ${missingConfig.join(', ')}`
}

function isTerminalJobStatus(status) {
  return status === 'succeeded' || status === 'failed'
}

function buildPollingPayload({ job, batch = null } = {}) {
  const batchId = batch?.id ?? job?.batchId ?? null

  return {
    jobId: job?.id ?? null,
    batchId,
    status: job?.status ?? null,
    isTerminal: isTerminalJobStatus(job?.status),
    pollPath: job?.id ? `/api/jobs/${job.id}` : null,
    batchPath: batchId != null ? `/api/batches/${batchId}` : null
  }
}

export function buildJobsRouter({ config, jobRunner, jobRepository } = {}) {
  if (!jobRunner?.start) {
    throw new Error('jobRunner.start is required')
  }

  if (!jobRepository?.getById) {
    throw new Error('jobRepository.getById is required')
  }

  const router = Router()

  router.post('/', asyncHandler(async (req, res) => {
    try {
      const requestedJob = normalizeJobRequest(req.body ?? {})

      if (requestedJob.aiEnhancement && !config?.ai?.ready) {
        res.status(400).json({
          ok: false,
          error: 'ai_not_ready',
          message: buildAiNotReadyMessage(config)
        })
        return
      }

      const startedJob = await jobRunner.start(requestedJob)
      res.status(202).json({
        ok: true,
        request: requestedJob,
        batch: startedJob.batch,
        job: startedJob.job,
        polling: buildPollingPayload(startedJob)
      })
    } catch (error) {
      if (error?.code === 'JOB_ALREADY_RUNNING') {
        res.status(409).json({
          ok: false,
          activeRun: error.activeRun,
          error: 'job_already_running'
        })
        return
      }

      error.code = error.code || 'job_start_failed'
      error.message = formatErrorMessage(error)
      throw error
    }
  }))

  router.get('/:jobId', asyncHandler(async (req, res) => {
    const jobId = parseJobId(req.params.jobId)

    if (jobId == null) {
      res.status(400).json({
        ok: false,
        error: 'invalid_job_id'
      })
      return
    }

    const job = jobRepository.findById ? await jobRepository.findById(jobId) : await jobRepository.getById(jobId)

    if (!job) {
      res.status(404).json({
        ok: false,
        error: 'job_not_found'
      })
      return
    }

    res.json({
      ok: true,
      job,
      polling: buildPollingPayload({ job })
    })
  }))

  return router
}
