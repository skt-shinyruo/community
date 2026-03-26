import assert from 'node:assert/strict'
import { createHmac } from 'node:crypto'
import test from 'node:test'

import { createCommunityApi } from '../src/integration/communityApi.mjs'

function decodeBase64UrlJson(segment) {
  const normalized = segment.replaceAll('-', '+').replaceAll('_', '/')
  const padding = '='.repeat((4 - (normalized.length % 4 || 4)) % 4)
  return JSON.parse(Buffer.from(`${normalized}${padding}`, 'base64').toString('utf8'))
}

function verifyHs256(token, secret) {
  const [encodedHeader, encodedPayload, encodedSignature] = token.split('.')
  const expectedSignature = createHmac('sha256', secret)
    .update(`${encodedHeader}.${encodedPayload}`)
    .digest('base64url')

  assert.equal(encodedSignature, expectedSignature)

  return {
    header: decodeBase64UrlJson(encodedHeader),
    payload: decodeBase64UrlJson(encodedPayload)
  }
}

test('communityApi attaches short-lived admin bearer token when reindex secret is configured', async () => {
  const requests = []
  const secret = 'dev-jwt-hmac-secret-please-change-me-123456'
  const api = createCommunityApi({
    config: {
      communityBaseUrl: 'http://community-app:8080',
      reindexAuth: {
        jwtHmacSecret: secret,
        jwtIssuer: 'community-auth',
        jwtTtlSeconds: 120
      }
    },
    fetchImpl: async (url, init = {}) => {
      requests.push({ url, init })
      return {
        ok: true
      }
    }
  })

  await api.reindexSearch()

  assert.equal(requests.length, 1)
  assert.equal(requests[0].url, 'http://community-app:8080/api/ops/search/reindex')
  assert.equal(requests[0].init.method, 'POST')
  assert.equal(requests[0].init.headers['content-type'], 'application/json')
  assert.match(requests[0].init.headers.authorization, /^Bearer /u)

  const token = requests[0].init.headers.authorization.slice('Bearer '.length)
  const { header, payload } = verifyHs256(token, secret)

  assert.equal(header.alg, 'HS256')
  assert.equal(header.typ, 'JWT')
  assert.equal(payload.iss, 'community-auth')
  assert.equal(payload.sub, 'mock-data-studio')
  assert.equal(payload.username, 'mock-data-studio')
  assert.deepEqual(payload.authorities, ['ROLE_ADMIN'])
  assert.ok(Number.isInteger(payload.iat))
  assert.ok(Number.isInteger(payload.exp))
  assert.ok(payload.exp > payload.iat)
})

test('communityApi omits bearer token when reindex secret is not configured', async () => {
  const requests = []
  const api = createCommunityApi({
    config: {
      communityBaseUrl: 'http://community-app:8080',
      reindexAuth: {
        jwtHmacSecret: null,
        jwtIssuer: 'community-auth',
        jwtTtlSeconds: 120
      }
    },
    fetchImpl: async (_url, init = {}) => {
      requests.push(init)
      return {
        ok: true
      }
    }
  })

  await api.reindexSearch()

  assert.equal(requests.length, 1)
  assert.equal(requests[0].headers.authorization, undefined)
})
