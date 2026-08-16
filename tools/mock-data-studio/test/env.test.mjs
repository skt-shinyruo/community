import assert from 'node:assert/strict'
import test from 'node:test'

import { loadConfig } from '../src/config/env.mjs'

const required = {
  MOCK_DATA_STUDIO_DB_URL: 'mysql://mysql:3306/community',
  MOCK_DATA_STUDIO_DB_USER: 'community',
  MOCK_DATA_STUDIO_DB_PASSWORD: 'communitypass'
}

test('loadConfig applies CLI defaults and trims credentials', () => {
  const config = loadConfig({
    ...required,
    MOCK_DATA_STUDIO_DB_USER: ' community ',
    MOCK_DATA_DEFAULT_USERS: '12'
  })

  assert.equal(config.db.user, 'community')
  assert.equal(config.communityBaseUrl, 'http://community-app:8080')
  assert.deepEqual(config.autoFill, {
    sceneKey: 'tech-community-hot-start',
    defaults: { users: 12, posts: 800, comments: 2500 }
  })
  assert.deepEqual(config.reindexAuth, {
    jwtHmacSecret: null,
    jwtIssuer: 'community-auth',
    jwtTtlSeconds: 120
  })
})

test('loadConfig validates required credentials and numeric settings', () => {
  assert.throws(() => loadConfig({ ...required, MOCK_DATA_STUDIO_DB_URL: '' }), /DB_URL is required/u)
  assert.throws(() => loadConfig({ ...required, MOCK_DATA_DEFAULT_POSTS: '-1' }), /non-negative integer/u)
  assert.throws(
    () => loadConfig({ ...required, MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS: '0' }),
    /positive integer/u
  )
})
