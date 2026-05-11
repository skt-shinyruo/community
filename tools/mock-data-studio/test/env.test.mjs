import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { loadConfig } from '../src/config/env.mjs'

const toolDir = fileURLToPath(new URL('..', import.meta.url))

test('loadConfig applies localhost-only defaults for bind host, listen port, and auto-fill planning flags', () => {
  const config = loadConfig({
    MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  })

  assert.equal(config.enabled, true)
  assert.equal(config.host, '127.0.0.1')
  assert.equal(config.port, 12888)
  assert.equal(config.autoFill.enabled, false)
  assert.equal(config.autoFill.sceneKey, 'tech-community-hot-start')
  assert.deepEqual(config.autoFill.defaults, {
    users: 100,
    posts: 800,
    comments: 2500
  })
  assert.deepEqual(config.ai, {
    provider: 'openai',
    enabled: false,
    apiKey: null,
    model: 'gpt-4.1-mini',
    timeoutMs: 8000,
    maxItemsPerJob: 20,
    ready: false,
    missingConfig: []
  })
})

test('loadConfig trims required DB env values', () => {
  const config = loadConfig({
    MOCK_DATA_STUDIO_DB_URL: '  mysql://mysql:3306/community  ',
    MOCK_DATA_STUDIO_DB_USER: '  community  ',
    MOCK_DATA_STUDIO_DB_PASSWORD: '  communitypass  '
  })

  assert.equal(config.db.url, 'mysql://mysql:3306/community')
  assert.equal(config.db.user, 'community')
  assert.equal(config.db.password, 'communitypass')
})

test('loadConfig rejects missing MOCK_DATA_STUDIO_DB_URL', () => {
  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_STUDIO_DB_USER: 'community',
        MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
      }),
    /MOCK_DATA_STUDIO_DB_URL is required/
  )
})

test('loadConfig rejects missing MOCK_DATA_STUDIO_DB_USER', () => {
  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
        MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
      }),
    /MOCK_DATA_STUDIO_DB_USER is required/
  )
})

test('loadConfig rejects missing MOCK_DATA_STUDIO_DB_PASSWORD', () => {
  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
        MOCK_DATA_STUDIO_DB_USER: 'community'
      }),
    /MOCK_DATA_STUDIO_DB_PASSWORD is required/
  )
})

test('loadConfig rejects invalid MOCK_DATA_STUDIO_PORT', () => {
  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_STUDIO_PORT: '70000',
        MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
        MOCK_DATA_STUDIO_DB_USER: 'community',
        MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
      }),
    /MOCK_DATA_STUDIO_PORT must be a valid TCP port/
  )
})

test('loadConfig rejects invalid auto-fill enabled boolean values', () => {
  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_AUTO_FILL_ENABLED: 'sometimes',
        MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
        MOCK_DATA_STUDIO_DB_USER: 'community',
        MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
      }),
    /MOCK_DATA_AUTO_FILL_ENABLED must be a boolean/
  )
})

test('loadConfig rejects invalid auto-fill default counts', () => {
  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_DEFAULT_USERS: '-1',
        MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
        MOCK_DATA_STUDIO_DB_USER: 'community',
        MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
      }),
    /MOCK_DATA_DEFAULT_USERS must be a non-negative integer/
  )
})

test('loadConfig enables AI config from mock-data-studio scoped OpenAI key', () => {
  const config = loadConfig({
    MOCK_DATA_STUDIO_AI_ENABLED: 'true',
    MOCK_DATA_STUDIO_OPENAI_API_KEY: 'test-key',
    MOCK_DATA_STUDIO_OPENAI_MODEL: 'gpt-4.1-mini',
    MOCK_DATA_STUDIO_OPENAI_TIMEOUT_MS: '9000',
    MOCK_DATA_STUDIO_AI_MAX_ITEMS_PER_JOB: '12',
    MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  })

  assert.equal(config.ai.enabled, true)
  assert.equal(config.ai.apiKey, 'test-key')
  assert.equal(config.ai.model, 'gpt-4.1-mini')
  assert.equal(config.ai.timeoutMs, 9000)
  assert.equal(config.ai.maxItemsPerJob, 12)
  assert.equal(config.ai.ready, true)
  assert.deepEqual(config.ai.missingConfig, [])
})

test('loadConfig does not read unscoped OpenAI key fallback', () => {
  const config = loadConfig({
    MOCK_DATA_STUDIO_AI_ENABLED: 'true',
    OPENAI_API_KEY: 'test-key',
    MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  })

  assert.equal(config.ai.apiKey, null)
  assert.equal(config.ai.ready, false)
  assert.deepEqual(config.ai.missingConfig, ['apiKey'])
})

test('loadConfig keeps AI optional and marks missing config when enabled without api key', () => {
  const config = loadConfig({
    MOCK_DATA_STUDIO_AI_ENABLED: 'true',
    MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  })

  assert.equal(config.ai.enabled, true)
  assert.equal(config.ai.ready, false)
  assert.deepEqual(config.ai.missingConfig, ['apiKey'])
})

test('loadConfig rejects invalid AI timeout/max-items values', () => {
  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_STUDIO_OPENAI_TIMEOUT_MS: '0',
        MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
        MOCK_DATA_STUDIO_DB_USER: 'community',
        MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
      }),
    /MOCK_DATA_STUDIO_OPENAI_TIMEOUT_MS must be a positive integer/
  )

  assert.throws(
    () =>
      loadConfig({
        MOCK_DATA_STUDIO_AI_MAX_ITEMS_PER_JOB: 'nope',
        MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
        MOCK_DATA_STUDIO_DB_USER: 'community',
        MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
      }),
    /MOCK_DATA_STUDIO_AI_MAX_ITEMS_PER_JOB must be a positive integer/
  )
})

test('loadConfig parses reindex auth secret from mock-data-studio scoped env only', () => {
  const config = loadConfig({
    MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET: 'dev-jwt-hmac-secret-please-change-me-123456',
    MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  })

  assert.deepEqual(config.reindexAuth, {
    jwtHmacSecret: 'dev-jwt-hmac-secret-please-change-me-123456',
    jwtIssuer: 'community-auth',
    jwtTtlSeconds: 120
  })
})

test('loadConfig does not read unscoped JWT_HMAC_SECRET fallback', () => {
  const config = loadConfig({
    JWT_HMAC_SECRET: 'dev-jwt-hmac-secret-please-change-me-123456',
    MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  })

  assert.deepEqual(config.reindexAuth, {
    jwtHmacSecret: null,
    jwtIssuer: 'community-auth',
    jwtTtlSeconds: 120
  })
})

test('loadConfig allows overriding reindex auth issuer and ttl', () => {
  const config = loadConfig({
    MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET: 'reindex-secret-1234567890abcdefghijklmnopqrstuvwxyz',
    MOCK_DATA_STUDIO_REINDEX_JWT_ISSUER: 'community-auth-dev',
    MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS: '45',
    MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  })

  assert.deepEqual(config.reindexAuth, {
    jwtHmacSecret: 'reindex-secret-1234567890abcdefghijklmnopqrstuvwxyz',
    jwtIssuer: 'community-auth-dev',
    jwtTtlSeconds: 45
  })
})

test('startup prints a prefixed config error and exits non-zero when required env is missing', () => {
  const hermeticEnv = {
    PATH: process.env.PATH,
    HOME: process.env.HOME,
    TMPDIR: process.env.TMPDIR,
    TEMP: process.env.TEMP,
    TMP: process.env.TMP,
    MOCK_DATA_STUDIO_DB_USER: 'community',
    MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
  }

  const result = spawnSync(process.execPath, ['src/server/index.mjs'], {
    cwd: toolDir,
    encoding: 'utf8',
    env: hermeticEnv,
    timeout: 2000
  })

  assert.notEqual(result.signal, 'SIGTERM')
  assert.notEqual(result.status, 0)
  assert.match(
    result.stderr,
    /\[mock-data-studio\] startup config error: MOCK_DATA_STUDIO_DB_URL is required/
  )
})
