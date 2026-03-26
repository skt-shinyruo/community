import { randomUUID } from 'node:crypto'

export const defaultJobPhaseNames = [
  'bootstrap',
  'plan',
  'write-community',
  'write-im',
  'reindex',
  'finalize'
]

function formatErrorMessage(error) {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return String(error)
}

function snapshotActiveRun(activeRun) {
  return {
    batchId: activeRun.batch?.id ?? null,
    jobId: activeRun.job?.id ?? null,
    state: activeRun.state
  }
}

function createConflictError(activeRun) {
  const error = new Error(`Another job is already ${activeRun.state}`)
  error.code = 'JOB_ALREADY_RUNNING'
  error.activeRun = snapshotActiveRun(activeRun)
  return error
}

function createDefaultPhases() {
  return defaultJobPhaseNames.map((name) => ({
    name,
    run: async () => {}
  }))
}

function normalizeMode(mode, batchType) {
  if (mode === 'auto-fill' || batchType === 'startup-auto-fill') {
    return 'auto-fill'
  }

  return 'manual-generate'
}

function createRunOptions({ mode, batchType, aiEnhancement = false } = {}) {
  const normalizedMode = normalizeMode(mode, batchType)
  return {
    mode: normalizedMode,
    aiEnhancement: normalizedMode === 'manual-generate' && Boolean(aiEnhancement)
  }
}

export function createJobRunner({
  batchRepository,
  jobRepository,
  phases = createDefaultPhases(),
  now = () => new Date().toISOString(),
  withRepositoriesTransaction = null,
  createBatchKey = () => `batch-${randomUUID()}`,
  createJobKey = () => `job-${randomUUID()}`
} = {}) {
  if (!batchRepository) {
    throw new Error('batchRepository is required')
  }

  if (!jobRepository) {
    throw new Error('jobRepository is required')
  }

  let activeRun = null

  async function withRepositories(work) {
    if (!withRepositoriesTransaction) {
      return work({ batchRepository, jobRepository })
    }

    return withRepositoriesTransaction(work)
  }

  async function persistPhaseSummary(jobId, completedPhases) {
    const summaryJson = {
      phases: completedPhases
    }

    return jobRepository.updateSummary(jobId, { summaryJson })
  }

  async function finalizeRun(
    runState,
    {
      finishedAt,
      status,
      completedPhases,
      errorMessage = null,
      batchFromStatuses = ['running'],
      jobFromStatuses = ['running']
    }
  ) {
    const jobSummaryJson = {
      phases: completedPhases
    }

    return withRepositories(async ({ batchRepository: txBatchRepository, jobRepository: txJobRepository }) => {
      const batch = runState.batch?.id
        ? await txBatchRepository.markFinished(runState.batch.id, {
            finishedAt,
            status,
            summaryJson: {
              jobId: runState.job?.id ?? null,
              phases: completedPhases
            },
            errorMessage,
            fromStatuses: batchFromStatuses
          })
        : null
      const job = runState.job?.id
        ? await txJobRepository.markFinished(runState.job.id, {
            finishedAt,
            status,
            summaryJson: jobSummaryJson,
            errorMessage,
            fromStatuses: jobFromStatuses
          })
        : null

      return {
        batch,
        job
      }
    })
  }

  async function failRun(runState, { completedPhases = [], errorMessage } = {}) {
    const finalized = await finalizeRun(runState, {
      finishedAt: now(),
      status: 'failed',
      completedPhases,
      errorMessage,
      batchFromStatuses: ['pending', 'running'],
      jobFromStatuses: ['pending', 'running']
    })

    runState.batch = finalized.batch ?? runState.batch
    runState.job = finalized.job ?? runState.job
    return finalized
  }

  async function prepareRunRecords({
    batch = null,
    requestedBy = 'mock-data-studio',
    batchType = 'demo-seed',
    jobType = 'demo-seed',
    mode,
    aiEnhancement = false,
    batchKey = createBatchKey(),
    jobKey = createJobKey(),
    createdAt = now()
  } = {}) {
    const runOptions = createRunOptions({
      mode,
      batchType,
      aiEnhancement
    })

    return withRepositories(async ({ batchRepository: txBatchRepository, jobRepository: txJobRepository }) => {
      const preparedBatch = batch?.id
        ? txBatchRepository.markPrepared
          ? await txBatchRepository.markPrepared(batch.id)
          : await txBatchRepository.getById(batch.id)
        : await txBatchRepository.create({
            batchKey,
            batchType,
            requestedBy,
            createdAt
          })
      const preparedJob = await txJobRepository.create({
        batchId: preparedBatch.id,
        jobKey,
        jobType,
        createdAt
      })

      return {
        batch: preparedBatch,
        job: preparedJob,
        runOptions
      }
    })
  }

  async function runLifecycle(runState) {
    const completedPhases = []

    try {
      const startedAt = now()
      const startedRecords = await withRepositories(
        async ({ batchRepository: txBatchRepository, jobRepository: txJobRepository }) => ({
          batch: await txBatchRepository.markStarted(runState.batch.id, { startedAt }),
          job: await txJobRepository.markStarted(runState.job.id, { startedAt })
        })
      )

      runState.batch = startedRecords.batch
      runState.job = startedRecords.job
      runState.state = 'running'
      runState.context = {}

      for (const phase of phases) {
        const phaseResult = await phase.run({
          batch: runState.batch,
          batchId: runState.batch.id,
          context: runState.context,
          job: runState.job,
          jobId: runState.job.id,
          phaseName: phase.name,
          runOptions: runState.runOptions
        })

        const completedPhase = {
          name: phase.name,
          completedAt: now()
        }

        if (phaseResult !== undefined) {
          completedPhase.result = structuredClone(phaseResult)
        }

        completedPhases.push(completedPhase)
        runState.job = await persistPhaseSummary(runState.job.id, completedPhases)
      }

      const finalized = await finalizeRun(runState, {
        finishedAt: now(),
        status: 'succeeded',
        completedPhases
      })
      runState.batch = finalized.batch
      runState.job = finalized.job

      return {
        status: 'succeeded'
      }
    } catch (error) {
      const errorMessage = formatErrorMessage(error)

      try {
        if (runState.batch?.id || runState.job?.id) {
          await failRun(runState, {
            completedPhases,
            errorMessage
          })
        }
      } catch (terminalError) {
        runState.finalizationError = terminalError
      }

      return {
        errorMessage,
        finalizationErrorMessage: runState.finalizationError
          ? formatErrorMessage(runState.finalizationError)
          : null,
        status: 'failed'
      }
    }
  }

  function launchRun(runState) {
    activeRun = runState
    runState.promise = runLifecycle(runState)
      .catch((error) => ({
        errorMessage: formatErrorMessage(error),
        finalizationErrorMessage: null,
        status: 'failed'
      }))
      .finally(() => {
        if (activeRun === runState) {
          activeRun = null
        }
      })

    return {
      batch: runState.batch,
      job: runState.job
    }
  }

  return {
    async prepare(options = {}) {
      return prepareRunRecords(options)
    },

    async startPrepared({ batch, job, runOptions } = {}) {
      if (!batch?.id) {
        throw new Error('batch is required')
      }

      if (!job?.id) {
        throw new Error('job is required')
      }

      if (activeRun) {
        throw createConflictError(activeRun)
      }

      const runState = {
        batch,
        job,
        state: 'starting',
        promise: Promise.resolve(),
        context: {},
        runOptions:
          runOptions ??
          createRunOptions({
            batchType: batch?.batchType
          })
      }

      return launchRun(runState)
    },

    async failPrepared({ batch, job } = {}, { errorMessage } = {}) {
      const runState = {
        batch: batch ?? null,
        job: job ?? null
      }

      await failRun(runState, {
        errorMessage: errorMessage ?? 'prepared run failed'
      })

      return {
        batch: runState.batch,
        job: runState.job
      }
    },

    async start({
      requestedBy = 'mock-data-studio',
      batchType = 'demo-seed',
      jobType = 'demo-seed',
      mode,
      aiEnhancement = false
    } = {}) {
      if (activeRun) {
        throw createConflictError(activeRun)
      }

      const runState = {
        batch: null,
        job: null,
        state: 'starting',
        promise: Promise.resolve(),
        context: {},
        runOptions: createRunOptions({
          mode,
          batchType,
          aiEnhancement
        })
      }

      activeRun = runState

      try {
        const createdRecords = await prepareRunRecords({
          requestedBy,
          batchType,
          jobType,
          mode,
          aiEnhancement
        })
        runState.batch = createdRecords.batch
        runState.job = createdRecords.job
        runState.runOptions = createdRecords.runOptions
      } catch (error) {
        if (runState.batch?.id || runState.job?.id) {
          try {
            await failRun(runState, {
              errorMessage: formatErrorMessage(error)
            })
          } catch (finalizeError) {
            runState.finalizationError = finalizeError
          }
        }
        activeRun = null
        throw error
      }

      return launchRun(runState)
    },

    getActiveRun() {
      if (!activeRun) {
        return null
      }

      return snapshotActiveRun(activeRun)
    },

    async waitForIdle() {
      if (!activeRun) {
        return
      }

      await activeRun.promise
    }
  }
}
