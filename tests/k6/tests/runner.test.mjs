import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { mkdtempSync, rmSync, mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { buildDockerArgs, collectEnvArgs, validateProfile } from '../scripts/runner-lib.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const suiteRoot = path.resolve(__dirname, '..')

describe('k6 docker runner', () => {
  it('rejects unknown profiles before building docker arguments', () => {
    assert.throws(
      () => validateProfile('unknown-profile', suiteRoot),
      /unknown profile: unknown-profile/
    )
  })

  it('forwards only supported k6 environment variables', () => {
    assert.deepEqual(
      collectEnvArgs({
        K6_BASE_URL: 'http://localhost:12880',
        K6_USERNAME: 'aaa',
        SHOULD_NOT_LEAK: 'secret'
      }),
      ['-e', 'K6_BASE_URL=http://localhost:12880', '-e', 'K6_USERNAME=aaa']
    )
  })

  it('builds a docker command for a known profile', () => {
    const tempRoot = mkdtempSync(path.join(tmpdir(), 'community-k6-runner-'))
    try {
      const tempSuite = path.join(tempRoot, 'suite')
      const tempRepo = path.join(tempRoot, 'repo')
      const resultsDir = path.join(tempRepo, 'temp', 'k6-results')
      mkdirSync(path.join(tempSuite, 'scenarios'), { recursive: true })
      mkdirSync(tempRepo, { recursive: true })
      writeFileSync(path.join(tempSuite, 'scenarios', 'smoke.js'), '')

      const command = buildDockerArgs({
        profile: 'smoke',
        suiteRoot: tempSuite,
        repoRoot: tempRepo,
        resultsDir,
        env: { K6_BASE_URL: 'http://localhost:12880' },
        platform: 'linux',
        now: new Date('2026-05-19T00:00:00Z')
      })

      assert.equal(command.cwd, tempRepo)
      assert.equal(command.summaryFile, path.join(resultsDir, 'smoke-2026-05-19T00-00-00-000Z.json'))
      assert.equal(command.args[0], 'run')
      assert.ok(command.args.includes('--rm'))
      assert.ok(command.args.includes('--network'))
      assert.ok(command.args.includes('host'))
      assert.ok(command.args.includes('--user'))
      assert.ok(command.args.includes(`${process.getuid()}:${process.getgid()}`))
      assert.ok(command.args.includes(`${tempSuite}:/scripts:ro`))
      assert.ok(command.args.includes(`${resultsDir}:/results`))
      assert.ok(command.args.includes('grafana/k6:0.51.0'))
      assert.ok(command.args.includes('--summary-export'))
      assert.ok(command.args.includes('/scripts/scenarios/smoke.js'))
      assert.ok(command.args.includes('K6_BASE_URL=http://localhost:12880'))
    } finally {
      rmSync(tempRoot, { recursive: true, force: true })
    }
  })

  it('allows overriding the k6 Docker image', () => {
    const tempRoot = mkdtempSync(path.join(tmpdir(), 'community-k6-runner-image-'))
    try {
      const tempSuite = path.join(tempRoot, 'suite')
      const tempRepo = path.join(tempRoot, 'repo')
      const resultsDir = path.join(tempRepo, 'temp', 'k6-results')
      mkdirSync(path.join(tempSuite, 'scenarios'), { recursive: true })
      mkdirSync(tempRepo, { recursive: true })
      writeFileSync(path.join(tempSuite, 'scenarios', 'smoke.js'), '')

      const command = buildDockerArgs({
        profile: 'smoke',
        suiteRoot: tempSuite,
        repoRoot: tempRepo,
        resultsDir,
        env: { K6_DOCKER_IMAGE: 'hubproxy.docker.internal:5555/grafana/k6:0.51.0' },
        platform: 'linux',
        now: new Date('2026-05-19T00:00:00Z')
      })

      assert.ok(command.args.includes('hubproxy.docker.internal:5555/grafana/k6:0.51.0'))
    } finally {
      rmSync(tempRoot, { recursive: true, force: true })
    }
  })
})
