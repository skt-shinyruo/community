import assert from 'node:assert/strict'
import test from 'node:test'

import request from 'supertest'

import { buildApp } from '../src/server/app.mjs'

const configId = '01965429-b34a-7000-8000-000000000001'

function existingConfig() {
  return {
    id: configId,
    name: 'primary',
    provider: 'openai',
    baseUrl: null,
    apiKey: 'secret-key',
    model: 'gpt-4.1-mini',
    enabled: true,
    isActive: true,
    timeoutMs: 8000,
    maxItemsPerJob: 20
  }
}

test('editing AI config without a replacement key preserves the stored key', async () => {
  let updateRequest
  const app = buildApp({
    config: { serviceName: 'mock-data-studio', ai: {} },
    aiConfigRepository: {
      async getById() { return existingConfig() },
      async update(_id, value) {
        updateRequest = value
        return { ...existingConfig(), ...value }
      },
      async list() { return [] },
      async getActive() { return existingConfig() }
    }
  })

  const response = await request(app).put(`/api/ai-config/${configId}`).send({
    name: 'renamed',
    provider: 'openai',
    model: 'gpt-4.1-mini',
    enabled: true,
    timeoutMs: 9000,
    maxItemsPerJob: 30
  })

  assert.equal(response.status, 200)
  assert.equal(updateRequest.apiKey, 'secret-key')
  assert.equal(response.body.data.apiKey, undefined)
})

test('testing a saved config uses its stored secret server-side', async () => {
  let testRequest
  const app = buildApp({
    config: { serviceName: 'mock-data-studio', ai: {} },
    aiConfigRepository: {
      async getById() { return existingConfig() },
      async list() { return [] },
      async getActive() { return existingConfig() },
      async testConnection(value) {
        testRequest = value
        return { success: true, message: 'ok' }
      }
    }
  })

  const response = await request(app).post(`/api/ai-config/${configId}/test`).send()

  assert.equal(response.status, 200)
  assert.equal(testRequest.apiKey, 'secret-key')
})
