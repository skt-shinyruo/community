import assert from 'node:assert/strict'
import test from 'node:test'

import request from 'supertest'

import { buildApp } from '../src/server/app.mjs'

function fakeConfig() {
  return {
    serviceName: 'mock-data-studio'
  }
}

test('health route returns ok payload', async () => {
  const app = buildApp({ config: fakeConfig() })

  const res = await request(app).get('/health')

  assert.equal(res.status, 200)
  assert.equal(res.body.ok, true)
  assert.equal(res.body.service, 'mock-data-studio')
  assert.equal(res.body.ui.title, 'Mock Data Studio')
  assert.equal(res.body.ui.apiBasePath, '/api')
  assert.equal(res.body.ui.generateForm.defaultDraft.jobRequest.batchType, 'demo-seed')
  assert.equal(res.body.ui.generateForm.modes.length, 2)
  assert.equal(res.body.ui.generateForm.scenePresets.length, 2)
})
