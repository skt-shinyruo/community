import { createBatchRepository } from '../batches/batchRepository.mjs'
import { createEntityRefRepository } from '../batches/entityRefRepository.mjs'
import { createTargetRepository } from '../batches/targetRepository.mjs'
import { createAiContentEnhancer } from '../ai/aiContentEnhancer.mjs'
import { createOpenAiClient } from '../ai/openaiClient.mjs'
import { loadConfig } from '../config/env.mjs'
import { bootstrapDemoSchema } from '../db/bootstrap.mjs'
import { createDb } from '../db/mysql.mjs'
import { createCommunityApi } from '../integration/communityApi.mjs'
import {
  createAutoFillJobPhases,
  createAutoFillService,
  defaultAutoFillMetadataBatchType
} from '../jobs/autoFillService.mjs'
import { createJobRepository } from '../jobs/jobRepository.mjs'
import { createJobRunner } from '../jobs/jobRunner.mjs'
import { createCommunityWriter } from '../writers/communityWriter.mjs'
import { createImWriter } from '../writers/imWriter.mjs'
import { buildApp } from './app.mjs'

function formatUrlHost(host) {
  if (host === '0.0.0.0' || host === '::') {
    return 'localhost'
  }

  if (host.includes(':') && !host.startsWith('[')) {
    return `[${host}]`
  }

  return host
}

function formatErrorMessage(error) {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return String(error)
}

function createRuntime({ config, db }) {
  const batchRepository = createBatchRepository(db)
  const entityRefRepository = createEntityRefRepository(db)
  const jobRepository = createJobRepository(db)
  const targetRepository = createTargetRepository(db)
  const communityApi = createCommunityApi({ config })
  const aiClient = createOpenAiClient({ config })
  const aiContentEnhancer = createAiContentEnhancer({
    config,
    aiClient
  })
  const communityWriter = createCommunityWriter({
    db,
    entityRefRepository,
    aiContentEnhancer
  })
  const imWriter = createImWriter({
    db,
    entityRefRepository,
    aiContentEnhancer
  })
  const autoFillService = createAutoFillService({
    batchRepository,
    config,
    communityApi,
    communityWriter,
    entityRefRepository,
    imWriter,
    targetRepository
  })
  const jobRunner = createJobRunner({
    batchRepository,
    jobRepository,
    phases: createAutoFillJobPhases({ autoFillService }),
    withRepositoriesTransaction: db?.withTransaction
      ? (work) =>
          db.withTransaction((txDb) =>
            work({
              batchRepository: createBatchRepository(txDb),
              jobRepository: createJobRepository(txDb)
            })
          )
      : null
  })

  return {
    app: buildApp({
      config,
      db,
      batchRepository,
      jobRepository,
      jobRunner
    }),
    autoFillService,
    batchRepository,
    jobRepository,
    jobRunner
  }
}

function startServer({ config, runtime }) {
  const { app } = runtime
  const displayHost = formatUrlHost(config.host)
  const server = app.listen(config.port, config.host, () => {
    console.log(
      `[${config.serviceName}] listening on http://${displayHost}:${config.port} ` +
        `(bind=${config.host}, community-app=${config.upstreams.communityAppBaseUrl}, ` +
        `im-core=${config.upstreams.imCoreBaseUrl}, studioEnabled=${config.enabled}, ` +
        `autoFill.enabled=${config.autoFill.enabled}, autoFill.scene=${config.autoFill.sceneKey})`
    )
  })

  server.on('error', (error) => {
    console.error(`[${config.serviceName}] startup server error: ${formatErrorMessage(error)}`)
    process.exit(1)
  })

  return server
}

async function triggerStartupAutoFill({ config, jobRunner, autoFillService }) {
  if (!config.autoFill.enabled) {
    return
  }

  let preparedRun = null

  try {
    const stableDefaultBatch = await autoFillService.ensureDefaultBatch({
      requestedBy: 'startup-auto-fill'
    })

    preparedRun = await jobRunner.prepare({
      batch: stableDefaultBatch,
      requestedBy: 'startup-auto-fill',
      batchType: defaultAutoFillMetadataBatchType,
      jobType: defaultAutoFillMetadataBatchType
    })

    const startedJob = await jobRunner.startPrepared(preparedRun)

    console.log(
      `[${config.serviceName}] startup auto-fill queued batch=${startedJob.batch.id} job=${startedJob.job.id}`
    )
  } catch (error) {
    if (preparedRun?.batch?.id || preparedRun?.job?.id) {
      try {
        await jobRunner.failPrepared(preparedRun, {
          errorMessage: formatErrorMessage(error)
        })
      } catch (finalizeError) {
        console.error(
          `[${config.serviceName}] startup auto-fill failure persistence error: ${formatErrorMessage(finalizeError)}`
        )
      }
    }

    console.error(`[${config.serviceName}] startup auto-fill launch error: ${formatErrorMessage(error)}`)
  }
}

async function main() {
  let config
  try {
    config = loadConfig()
  } catch (error) {
    console.error(`[mock-data-studio] startup config error: ${formatErrorMessage(error)}`)
    process.exit(1)
  }

  let db

  try {
    if (!config.enabled) {
      console.log(`[${config.serviceName}] disabled by MOCK_DATA_STUDIO_ENABLED=false`)
      return
    }

    db = await createDb(config)
    await bootstrapDemoSchema(db)
    const runtime = createRuntime({ db, config })
    startServer({ config, runtime })
    await triggerStartupAutoFill({ config, jobRunner: runtime.jobRunner, autoFillService: runtime.autoFillService })
  } catch (error) {
    if (db?.end) {
      await db.end().catch(() => {})
    }

    console.error(`[${config.serviceName}] startup bootstrap error: ${formatErrorMessage(error)}`)
    process.exit(1)
  }
}

await main()
