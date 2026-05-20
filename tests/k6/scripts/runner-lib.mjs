import { existsSync, mkdirSync } from 'node:fs'
import process from 'node:process'
import path from 'node:path'

export const supportedProfiles = ['smoke', 'api-mix', 'write-paths', 'im-ws', 'soak', 'stress', 'spike']
export const defaultK6DockerImage = 'grafana/k6:0.51.0'

export const forwardedEnvNames = [
  'K6_BASE_URL',
  'K6_WS_URL',
  'K6_USERNAME',
  'K6_PASSWORD',
  'K6_SECONDARY_USERNAME',
  'K6_SECONDARY_PASSWORD',
  'K6_ADMIN_USERNAME',
  'K6_ADMIN_PASSWORD',
  'K6_LOGIN_EACH_ITERATION',
  'K6_WRITE_RATIO',
  'K6_READ_SIZE',
  'K6_THINK_MIN_MS',
  'K6_THINK_MAX_MS',
  'K6_IM_HOLD_SECONDS',
  'K6_IM_PING_INTERVAL_SECONDS',
  'K6_IM_SEND_MESSAGES',
  'K6_IM_ROOM_ID',
  'K6_POST_TAG',
  'K6_POST_CATEGORY_ID',
  'K6_ALLOW_WRITES',
  'K6_ALLOW_DESTRUCTIVE_WRITES',
  'K6_HTTP_FAILED_RATE',
  'K6_HTTP_P95_MS',
  'K6_HTTP_P99_MS',
  'K6_CHECK_RATE',
  'K6_WS_CONNECT_P95_MS',
  'K6_WS_SESSION_P95_MIN_MS',
  'K6_NO_CONNECTION_REUSE',
  'K6_USER_AGENT'
]

export function validateProfile(profile, suiteRoot) {
  const scenarioPath = path.join(suiteRoot, 'scenarios', `${profile}.js`)
  if (!supportedProfiles.includes(profile) || !existsSync(scenarioPath)) {
    throw new Error(`unknown profile: ${profile}`)
  }
  return scenarioPath
}

export function collectEnvArgs(env = process.env) {
  const envArgs = []
  for (const name of forwardedEnvNames) {
    if (env[name] !== undefined) {
      envArgs.push('-e', `${name}=${env[name]}`)
    }
  }
  return envArgs
}

export function buildDockerArgs({
  profile,
  suiteRoot,
  repoRoot,
  resultsDir,
  env = process.env,
  platform = process.platform,
  now = new Date()
}) {
  validateProfile(profile, suiteRoot)
  mkdirSync(resultsDir, { recursive: true })

  const timestamp = now.toISOString().replace(/[:.]/g, '-')
  const summaryPath = `/results/${profile}-${timestamp}.json`
  const networkArgs = platform === 'linux' ? ['--network', 'host'] : []
  const userArgs = typeof process.getuid === 'function' && typeof process.getgid === 'function'
    ? ['--user', `${process.getuid()}:${process.getgid()}`]
    : []
  const k6Image = env.K6_DOCKER_IMAGE || defaultK6DockerImage

  return {
    summaryFile: path.join(resultsDir, path.basename(summaryPath)),
    args: [
      'run',
      '--rm',
      ...networkArgs,
      ...userArgs,
      '-v', `${suiteRoot}:/scripts:ro`,
      '-v', `${resultsDir}:/results`,
      ...collectEnvArgs(env),
      k6Image,
      'run',
      '--summary-export', summaryPath,
      `/scripts/scenarios/${profile}.js`
    ],
    cwd: repoRoot
  }
}
