import { fileURLToPath } from 'node:url'

import express from 'express'

import { createBatchRepository } from '../batches/batchRepository.mjs'
import { createEntityRefRepository } from '../batches/entityRefRepository.mjs'
import { createTargetRepository } from '../batches/targetRepository.mjs'
import { createJobRepository } from '../jobs/jobRepository.mjs'
import { createJobRunner } from '../jobs/jobRunner.mjs'
import { createDeleteBatchService } from '../writers/deleteBatchService.mjs'
import { buildBatchesRouter } from './routes/batches.mjs'
import { buildHealthRouter } from './routes/health.mjs'
import { buildJobsRouter } from './routes/jobs.mjs'
import {
  buildRuntimeStatusRouter,
  createRuntimeStatusService
} from './routes/runtimeStatus.mjs'

const uiDirectoryPath = fileURLToPath(new URL('../ui/', import.meta.url))

export function buildApp({
  config,
  db,
  fetchImpl,
  batchRepository,
  jobRepository,
  targetRepository,
  entityRefRepository,
  deleteBatchService,
  jobRunner,
  runtimeStatusService
} = {}) {
  const app = express()
  const resolvedBatchRepository = batchRepository ?? (db ? createBatchRepository(db) : null)
  const resolvedJobRepository = jobRepository ?? (db ? createJobRepository(db) : null)
  const resolvedTargetRepository = targetRepository ?? (db ? createTargetRepository(db) : null)
  const resolvedEntityRefRepository = entityRefRepository ?? (db ? createEntityRefRepository(db) : null)
  const withRepositoriesTransaction =
    !jobRunner && !batchRepository && !jobRepository && db?.withTransaction
      ? (work) =>
          db.withTransaction((txDb) =>
            work({
              batchRepository: createBatchRepository(txDb),
              jobRepository: createJobRepository(txDb)
            })
          )
      : null
  const resolvedDeleteBatchService =
    deleteBatchService ??
    (db && resolvedBatchRepository && resolvedJobRepository && resolvedEntityRefRepository
      ? createDeleteBatchService({
          db,
          batchRepository: resolvedBatchRepository,
          jobRepository: resolvedJobRepository,
          entityRefRepository: resolvedEntityRefRepository
        })
      : null)
  const resolvedJobRunner =
    jobRunner ??
    (resolvedBatchRepository && resolvedJobRepository
      ? createJobRunner({
          batchRepository: resolvedBatchRepository,
          jobRepository: resolvedJobRepository,
          withRepositoriesTransaction
        })
      : null)
  const resolvedRuntimeStatusService =
    runtimeStatusService ?? createRuntimeStatusService({ config, db, fetchImpl })

  app.disable('x-powered-by')
  app.use(express.json())
  app.use(express.static(uiDirectoryPath, { index: 'index.html' }))

  app.use('/health', buildHealthRouter({ config }))
  app.use('/api/runtime-status', buildRuntimeStatusRouter({ runtimeStatusService: resolvedRuntimeStatusService }))

  if (resolvedJobRunner && resolvedJobRepository) {
    app.use(
      '/api/jobs',
      buildJobsRouter({
        config,
        jobRepository: resolvedJobRepository,
        jobRunner: resolvedJobRunner
      })
    )
  }

  if (
    resolvedBatchRepository &&
    resolvedJobRepository &&
    resolvedTargetRepository &&
    resolvedEntityRefRepository &&
    resolvedDeleteBatchService
  ) {
    app.use(
      '/api/batches',
      buildBatchesRouter({
        batchRepository: resolvedBatchRepository,
        jobRepository: resolvedJobRepository,
        targetRepository: resolvedTargetRepository,
        entityRefRepository: resolvedEntityRefRepository,
        deleteBatchService: resolvedDeleteBatchService
      })
    )
  }

  app.use((error, _req, res, _next) => {
    if (res.headersSent) {
      return
    }

    const status = Number.isInteger(error?.status) ? error.status : 500

    res.status(status).json({
      ok: false,
      error: error?.code || 'internal_error',
      message: error?.message || 'Internal Server Error'
    })
  })

  return app
}
