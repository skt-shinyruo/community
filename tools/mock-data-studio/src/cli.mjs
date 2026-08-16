import { randomUUID } from 'node:crypto'
import { resolve } from 'node:path'
import { parseArgs } from 'node:util'
import { pathToFileURL } from 'node:url'

import { createBatchRepository } from './batches/batchRepository.mjs'
import { createEntityRefRepository } from './batches/entityRefRepository.mjs'
import { createTargetRepository } from './batches/targetRepository.mjs'
import { loadConfig } from './config/env.mjs'
import { createDb } from './db/mysql.mjs'
import { createPlanner } from './generator/planner.mjs'
import { createCommunityApi } from './integration/communityApi.mjs'
import { createCommunityWriter } from './writers/communityWriter.mjs'
import { createDeleteBatchService } from './writers/deleteBatchService.mjs'
import { createImWriter } from './writers/imWriter.mjs'

const HELP = `Usage:
  mock-data-studio generate [--scene NAME] [--seed TEXT] [--users N] [--posts N] [--comments N]
  mock-data-studio delete [--force] <batch-id>

Commands run synchronously and print JSON. Database credentials come from
MOCK_DATA_STUDIO_DB_URL, MOCK_DATA_STUDIO_DB_USER, and MOCK_DATA_STUDIO_DB_PASSWORD.`

function count(value, name) {
  if (value == null) return undefined
  if (!/^\d+$/u.test(value)) throw new Error(`--${name} must be a non-negative integer`)
  return Number.parseInt(value, 10)
}

export function parseCommand(args) {
  const { positionals, values } = parseArgs({
    args,
    allowPositionals: true,
    strict: true,
    options: {
      help: { type: 'boolean', short: 'h' },
      force: { type: 'boolean' },
      scene: { type: 'string' },
      seed: { type: 'string' },
      users: { type: 'string' },
      posts: { type: 'string' },
      comments: { type: 'string' }
    }
  })

  if (values.help) return { name: 'help' }

  const [name, batchId, ...extra] = positionals
  if (extra.length > 0) throw new Error(`unexpected argument: ${extra[0]}`)
  if (name === 'delete') {
    if (!batchId) throw new Error('delete requires a batch id')
    if (values.scene || values.seed || values.users || values.posts || values.comments) {
      throw new Error('generate options cannot be used with delete')
    }
    return { name, batchId, force: Boolean(values.force) }
  }
  if (name !== 'generate') throw new Error(`unknown command: ${name ?? '(missing)'}`)
  if (batchId) throw new Error(`unexpected argument: ${batchId}`)
  if (values.force) throw new Error('--force can only be used with delete')

  return {
    name,
    scene: values.scene,
    seed: values.seed,
    counts: Object.fromEntries(
      ['users', 'posts', 'comments']
        .map((key) => [key, count(values[key], key)])
        .filter(([, value]) => value !== undefined)
    )
  }
}

function mergeCounts(...counts) {
  return Object.assign({}, ...counts)
}

async function reindexGeneratedContent({ generatedRefs, communityApi, enabled }) {
  if (!generatedRefs.some((ref) => ref.entityType === 'posts' || ref.entityType === 'comments')) {
    return { attempted: false, reason: 'no-content' }
  }
  if (!enabled) return { attempted: false, reason: 'missing-reindex-secret' }

  try {
    await communityApi.reindexSearch()
    return { attempted: true, succeeded: true }
  } catch (error) {
    return { attempted: true, succeeded: false, error: error?.message ?? String(error) }
  }
}

export async function generateBatch({
  options,
  config,
  batchRepository,
  planner,
  communityWriter,
  imWriter,
  communityApi,
  createBatchKey = () => `cli-${randomUUID()}`
}) {
  let batch = await batchRepository.create({
    batchKey: createBatchKey(),
    batchType: 'demo-seed',
    requestedBy: 'cli'
  })

  try {
    batch = await batchRepository.markStarted(batch.id)
    const plan = await planner.planDefaultBatch({
      batchId: batch.id,
      sceneKey: options.scene ?? config.autoFill.sceneKey
    })
    const seed = options.seed ?? batch.id
    const community = await communityWriter.writePhase({ batchId: batch.id, plan, seed })
    const im = await imWriter.writePhase({ batchId: batch.id, plan, seed })
    const generatedRefs = [...community.generatedRefs, ...im.generatedRefs]
    const summary = {
      scene: plan.sceneKey,
      seed,
      insertedCounts: mergeCounts(community.insertedCounts, im.insertedCounts),
      reindex: await reindexGeneratedContent({
        generatedRefs,
        communityApi,
        enabled: Boolean(config.reindexAuth.jwtHmacSecret)
      })
    }

    await batchRepository.markFinished(batch.id, { status: 'succeeded', summaryJson: summary })
    return { batchId: batch.id, status: 'succeeded', ...summary }
  } catch (error) {
    await batchRepository.markFinished(batch.id, {
      status: 'failed',
      errorMessage: error?.message ?? String(error),
      fromStatuses: ['pending', 'running']
    }).catch(() => {})
    throw error
  }
}

export async function runCommand(command, config, db) {
  const batchRepository = createBatchRepository(db)
  const entityRefRepository = createEntityRefRepository(db)

  if (command.name === 'delete') {
    return createDeleteBatchService({ db, batchRepository, entityRefRepository }).deleteBatch(
      command.batchId,
      { force: command.force }
    )
  }

  const runConfig = {
    ...config,
    autoFill: {
      ...config.autoFill,
      defaults: { ...config.autoFill.defaults, ...command.counts }
    }
  }
  const targetRepository = createTargetRepository(db)
  return generateBatch({
    options: command,
    config: runConfig,
    batchRepository,
    planner: createPlanner({ config: runConfig, targetRepository, entityRefRepository }),
    communityWriter: createCommunityWriter({ db, entityRefRepository }),
    imWriter: createImWriter({ db, entityRefRepository }),
    communityApi: createCommunityApi({ config: runConfig })
  })
}

export async function main(args = process.argv.slice(2), env = process.env) {
  const command = parseCommand(args)
  if (command.name === 'help') {
    console.log(HELP)
    return
  }

  const config = loadConfig(env)
  const db = await createDb(config)
  try {
    console.log(JSON.stringify(await runCommand(command, config, db), null, 2))
  } finally {
    await db.end()
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(`[mock-data-studio] ${error?.message ?? String(error)}`)
    process.exitCode = 1
  })
}
