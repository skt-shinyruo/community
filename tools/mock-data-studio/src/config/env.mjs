const DEFAULT_PORT = 12888
const DEFAULT_BIND_HOST = '127.0.0.1'
const DEFAULT_SERVICE_NAME = 'mock-data-studio'
const DEFAULT_COMMUNITY_APP_BASE_URL = 'http://community-app:8080'
const DEFAULT_IM_CORE_BASE_URL = 'http://im-core:18082'
const DEFAULT_AUTO_FILL_SCENE_KEY = 'tech-community-hot-start'
const DEFAULT_AI_MODEL = 'gpt-4.1-mini'
const DEFAULT_AI_TIMEOUT_MS = 8000
const DEFAULT_AI_MAX_ITEMS_PER_JOB = 20
const DEFAULT_REINDEX_JWT_ISSUER = 'community-auth'
const DEFAULT_REINDEX_JWT_TTL_SECONDS = 120

const TRUE_VALUES = new Set(['1', 'true', 'yes', 'on'])
const FALSE_VALUES = new Set(['0', 'false', 'no', 'off'])

function parseRequired(env, name) {
  const value = env[name]

  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`${name} is required`)
  }

  return value.trim()
}

function parsePort(value, fallback) {
  if (value === undefined || value === null || value === '') {
    return fallback
  }

  const normalized = String(value).trim()

  if (!/^\d+$/.test(normalized)) {
    throw new Error(`MOCK_DATA_STUDIO_PORT must be a valid TCP port`)
  }

  const parsed = Number.parseInt(normalized, 10)

  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
    throw new Error(`MOCK_DATA_STUDIO_PORT must be a valid TCP port`)
  }

  return parsed
}

function parseOptionalString(value, fallback) {
  if (typeof value !== 'string') {
    return fallback
  }

  const normalized = value.trim()

  return normalized === '' ? fallback : normalized
}

function parseBoolean(value, fallback, name) {
  if (value === undefined || value === null || value === '') {
    return fallback
  }

  const normalized = String(value).trim().toLowerCase()

  if (TRUE_VALUES.has(normalized)) {
    return true
  }

  if (FALSE_VALUES.has(normalized)) {
    return false
  }

  throw new Error(`${name} must be a boolean`)
}

function parseNonNegativeInteger(value, fallback, name) {
  if (value === undefined || value === null || value === '') {
    return fallback
  }

  const normalized = String(value).trim()

  if (!/^\d+$/u.test(normalized)) {
    throw new Error(`${name} must be a non-negative integer`)
  }

  return Number.parseInt(normalized, 10)
}

function parsePositiveInteger(value, fallback, name) {
  if (value === undefined || value === null || value === '') {
    return fallback
  }

  const normalized = String(value).trim()

  if (!/^\d+$/u.test(normalized)) {
    throw new Error(`${name} must be a positive integer`)
  }

  const parsed = Number.parseInt(normalized, 10)
  if (parsed <= 0) {
    throw new Error(`${name} must be a positive integer`)
  }

  return parsed
}

function hasConfiguredValue(value) {
  return typeof value === 'string' && value.trim() !== ''
}

export function loadConfig(env = process.env) {
  const communityAppBaseUrl = parseOptionalString(
    env.MOCK_DATA_STUDIO_COMMUNITY_APP_BASE_URL,
    DEFAULT_COMMUNITY_APP_BASE_URL
  )
  const imCoreBaseUrl = parseOptionalString(
    env.MOCK_DATA_STUDIO_IM_CORE_BASE_URL,
    DEFAULT_IM_CORE_BASE_URL
  )
  const aiEnabled = parseBoolean(env.MOCK_DATA_STUDIO_AI_ENABLED, false, 'MOCK_DATA_STUDIO_AI_ENABLED')
  const aiApiKey = parseOptionalString(env.MOCK_DATA_STUDIO_OPENAI_API_KEY, null)
  const aiMissingConfig = []

  if (aiEnabled && !hasConfiguredValue(aiApiKey)) {
    aiMissingConfig.push('apiKey')
  }

  return {
    enabled: parseBoolean(env.MOCK_DATA_STUDIO_ENABLED, true, 'MOCK_DATA_STUDIO_ENABLED'),
    serviceName: DEFAULT_SERVICE_NAME,
    host: parseOptionalString(env.MOCK_DATA_STUDIO_BIND_HOST, DEFAULT_BIND_HOST),
    port: parsePort(env.MOCK_DATA_STUDIO_PORT, DEFAULT_PORT),
    communityBaseUrl: communityAppBaseUrl,
    autoFill: {
      enabled: parseBoolean(env.MOCK_DATA_AUTO_FILL_ENABLED, false, 'MOCK_DATA_AUTO_FILL_ENABLED'),
      sceneKey: parseOptionalString(env.MOCK_DATA_AUTO_FILL_SCENE, DEFAULT_AUTO_FILL_SCENE_KEY),
      defaults: {
        users: parseNonNegativeInteger(env.MOCK_DATA_DEFAULT_USERS, 100, 'MOCK_DATA_DEFAULT_USERS'),
        posts: parseNonNegativeInteger(env.MOCK_DATA_DEFAULT_POSTS, 800, 'MOCK_DATA_DEFAULT_POSTS'),
        comments: parseNonNegativeInteger(env.MOCK_DATA_DEFAULT_COMMENTS, 2500, 'MOCK_DATA_DEFAULT_COMMENTS')
      }
    },
    db: {
      url: parseRequired(env, 'MOCK_DATA_STUDIO_DB_URL'),
      user: parseRequired(env, 'MOCK_DATA_STUDIO_DB_USER'),
      password: parseRequired(env, 'MOCK_DATA_STUDIO_DB_PASSWORD')
    },
    upstreams: {
      communityAppBaseUrl,
      imCoreBaseUrl
    },
    reindexAuth: {
      jwtHmacSecret: parseOptionalString(env.MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET, null),
      jwtIssuer: parseOptionalString(env.MOCK_DATA_STUDIO_REINDEX_JWT_ISSUER, DEFAULT_REINDEX_JWT_ISSUER),
      jwtTtlSeconds: parsePositiveInteger(
        env.MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS,
        DEFAULT_REINDEX_JWT_TTL_SECONDS,
        'MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS'
      )
    },
    ai: {
      provider: 'openai',
      enabled: aiEnabled,
      apiKey: aiApiKey,
      model: parseOptionalString(env.MOCK_DATA_STUDIO_OPENAI_MODEL, DEFAULT_AI_MODEL),
      timeoutMs: parsePositiveInteger(
        env.MOCK_DATA_STUDIO_OPENAI_TIMEOUT_MS,
        DEFAULT_AI_TIMEOUT_MS,
        'MOCK_DATA_STUDIO_OPENAI_TIMEOUT_MS'
      ),
      maxItemsPerJob: parsePositiveInteger(
        env.MOCK_DATA_STUDIO_AI_MAX_ITEMS_PER_JOB,
        DEFAULT_AI_MAX_ITEMS_PER_JOB,
        'MOCK_DATA_STUDIO_AI_MAX_ITEMS_PER_JOB'
      ),
      ready: aiEnabled && aiMissingConfig.length === 0,
      missingConfig: aiMissingConfig
    }
  }
}
