import { Router } from 'express'

import { normalizeUuid } from '../../db/uuidv7.mjs'
import { asyncHandler } from './asyncHandler.mjs'

function maskApiKey(key) {
  if (!key) return null
  if (key.length <= 8) return '••••'
  return key.slice(0, 4) + '••••' + key.slice(-4)
}

function sanitizeConfig(cfg) {
  return {
    ...cfg,
    apiKeyMasked: maskApiKey(cfg.apiKey),
    apiKey: undefined
  }
}

function parseConfigId(value) {
  try {
    return normalizeUuid(value)
  } catch {
    return null
  }
}

export function buildAiConfigRouter({ aiConfigRepository } = {}) {
  if (!aiConfigRepository) {
    throw new Error('aiConfigRepository is required')
  }

  const router = Router()

  router.get('/', asyncHandler(async (_req, res) => {
    const configs = await aiConfigRepository.list()
    res.json({
      ok: true,
      data: configs.map(sanitizeConfig)
    })
  }))

  router.get('/active', asyncHandler(async (_req, res) => {
    const config = await aiConfigRepository.getActive()
    if (!config) {
      return res.status(404).json({
        ok: false,
        error: 'not_found',
        message: 'No active AI configuration'
      })
    }
    res.json({
      ok: true,
      data: sanitizeConfig(config)
    })
  }))

  router.post('/', asyncHandler(async (req, res) => {
    const { name, provider, baseUrl, apiKey, model, enabled, timeoutMs, maxItemsPerJob } = req.body

    if (!name || !provider || !model) {
      return res.status(400).json({
        ok: false,
        error: 'validation_error',
        message: 'name, provider and model are required'
      })
    }

    const saved = await aiConfigRepository.create({
      name,
      provider,
      baseUrl: baseUrl || null,
      apiKey: apiKey || null,
      model,
      enabled: Boolean(enabled),
      timeoutMs: Number(timeoutMs) || 8000,
      maxItemsPerJob: Number(maxItemsPerJob) || 20
    })

    res.status(201).json({
      ok: true,
      data: sanitizeConfig(saved)
    })
  }))

  router.put('/:id', asyncHandler(async (req, res) => {
    const id = parseConfigId(req.params.id)
    const { name, provider, baseUrl, apiKey, model, enabled, timeoutMs, maxItemsPerJob } = req.body

    if (!id) {
      return res.status(400).json({
        ok: false,
        error: 'validation_error',
        message: 'invalid configuration id'
      })
    }

    if (!provider || !model) {
      return res.status(400).json({
        ok: false,
        error: 'validation_error',
        message: 'provider and model are required'
      })
    }

    const existing = await aiConfigRepository.getById(id)
    if (!existing) {
      return res.status(404).json({
        ok: false,
        error: 'not_found',
        message: 'Configuration not found'
      })
    }

    const saved = await aiConfigRepository.update(id, {
      name: name || existing.name,
      provider,
      baseUrl: baseUrl !== undefined ? baseUrl : existing.baseUrl,
      apiKey: apiKey || undefined,
      model,
      enabled: Boolean(enabled),
      timeoutMs: Number(timeoutMs) || existing.timeoutMs,
      maxItemsPerJob: Number(maxItemsPerJob) || existing.maxItemsPerJob
    })

    res.json({
      ok: true,
      data: sanitizeConfig(saved)
    })
  }))

  router.post('/:id/activate', asyncHandler(async (req, res) => {
    const id = parseConfigId(req.params.id)
    if (!id) {
      return res.status(400).json({
        ok: false,
        error: 'validation_error',
        message: 'invalid configuration id'
      })
    }
    const existing = await aiConfigRepository.getById(id)
    if (!existing) {
      return res.status(404).json({
        ok: false,
        error: 'not_found',
        message: 'Configuration not found'
      })
    }

    const activated = await aiConfigRepository.setActive(id)
    res.json({
      ok: true,
      data: sanitizeConfig(activated)
    })
  }))

  router.delete('/:id', asyncHandler(async (req, res) => {
    const id = parseConfigId(req.params.id)
    if (!id) {
      return res.status(400).json({
        ok: false,
        error: 'validation_error',
        message: 'invalid configuration id'
      })
    }
    await aiConfigRepository.delete(id)
    res.json({ ok: true })
  }))

  router.post('/test', asyncHandler(async (req, res) => {
    const { provider, baseUrl, apiKey, model, timeoutMs } = req.body

    if (!provider || !model) {
      return res.status(400).json({
        ok: false,
        error: 'validation_error',
        message: 'provider and model are required for testing'
      })
    }

    const result = await aiConfigRepository.testConnection({
      provider,
      baseUrl: baseUrl || null,
      apiKey: apiKey || null,
      model,
      timeoutMs: Number(timeoutMs) || 8000
    })

    res.json({
      ok: result.success,
      message: result.message
    })
  }))

  return router
}
