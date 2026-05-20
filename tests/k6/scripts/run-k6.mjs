#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildDockerArgs, supportedProfiles, validateProfile } from './runner-lib.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const suiteRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(suiteRoot, '..', '..')

const profile = process.argv[2] || 'smoke'
try {
  validateProfile(profile, suiteRoot)
} catch (_) {
  console.error(`[k6] unknown profile: ${profile}`)
  console.error(`[k6] supported profiles: ${supportedProfiles.join(', ')}`)
  process.exit(2)
}

const resultsDir = path.join(repoRoot, 'temp', 'k6-results')
const command = buildDockerArgs({ profile, suiteRoot, repoRoot, resultsDir })

console.log(`[k6] profile=${profile}`)
console.log(`[k6] summary=${command.summaryFile}`)
console.log(`[k6] image=${process.env.K6_DOCKER_IMAGE || 'grafana/k6:0.51.0'}`)

const result = spawnSync('docker', command.args, {
  stdio: 'inherit',
  cwd: command.cwd
})

if (result.error) {
  console.error(`[k6] failed to start docker: ${result.error.message}`)
  process.exit(1)
}
process.exit(result.status ?? 1)
