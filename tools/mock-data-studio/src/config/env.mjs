const DEFAULT_COMMUNITY_APP_BASE_URL = 'http://community-app:8080'
const DEFAULT_SCENE_KEY = 'tech-community-hot-start'
const DEFAULT_REINDEX_JWT_ISSUER = 'community-auth'

function required(env, name) {
  const value = env[name]?.trim()
  if (!value) throw new Error(`${name} is required`)
  return value
}

function optional(value, fallback) {
  const normalized = value?.trim()
  return normalized || fallback
}

function nonNegativeInteger(value, fallback, name) {
  if (value == null || value === '') return fallback
  if (!/^\d+$/u.test(String(value).trim())) throw new Error(`${name} must be a non-negative integer`)
  return Number.parseInt(value, 10)
}

function positiveInteger(value, fallback, name) {
  const parsed = nonNegativeInteger(value, fallback, name)
  if (parsed < 1) throw new Error(`${name} must be a positive integer`)
  return parsed
}

export function loadConfig(env = process.env) {
  return {
    communityBaseUrl: optional(
      env.MOCK_DATA_STUDIO_COMMUNITY_APP_BASE_URL,
      DEFAULT_COMMUNITY_APP_BASE_URL
    ),
    autoFill: {
      sceneKey: optional(env.MOCK_DATA_AUTO_FILL_SCENE, DEFAULT_SCENE_KEY),
      defaults: {
        users: nonNegativeInteger(env.MOCK_DATA_DEFAULT_USERS, 100, 'MOCK_DATA_DEFAULT_USERS'),
        posts: nonNegativeInteger(env.MOCK_DATA_DEFAULT_POSTS, 800, 'MOCK_DATA_DEFAULT_POSTS'),
        comments: nonNegativeInteger(env.MOCK_DATA_DEFAULT_COMMENTS, 2500, 'MOCK_DATA_DEFAULT_COMMENTS')
      }
    },
    db: {
      url: required(env, 'MOCK_DATA_STUDIO_DB_URL'),
      user: required(env, 'MOCK_DATA_STUDIO_DB_USER'),
      password: required(env, 'MOCK_DATA_STUDIO_DB_PASSWORD')
    },
    reindexAuth: {
      jwtHmacSecret: optional(env.MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET, null),
      jwtIssuer: optional(env.MOCK_DATA_STUDIO_REINDEX_JWT_ISSUER, DEFAULT_REINDEX_JWT_ISSUER),
      jwtTtlSeconds: positiveInteger(
        env.MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS,
        120,
        'MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS'
      )
    }
  }
}
