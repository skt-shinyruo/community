import { Router } from 'express'

import { asyncHandler } from './asyncHandler.mjs'

function formatErrorMessage(error) {
  if (error instanceof Error && error.message) {
    return error.message
  }

  return String(error)
}

const runtimeCardLabels = {
  db: '数据库',
  communityApp: 'community-app',
  imCore: 'im-core',
  ai: 'AI 环境'
}

function buildHealthUrl(baseUrl) {
  if (typeof baseUrl !== 'string' || baseUrl.trim() === '') {
    return null
  }

  return new URL('/actuator/health', baseUrl).toString()
}

async function defaultProbeUrl(url, { fetchImpl = globalThis.fetch } = {}) {
  if (typeof fetchImpl !== 'function') {
    return {
      ready: false,
      error: 'fetch is unavailable',
      status: null
    }
  }

  try {
    const response = await fetchImpl(url, {
      method: 'GET',
      signal: AbortSignal.timeout(1500)
    })

    return {
      ready: response.ok,
      status: response.status
    }
  } catch (error) {
    return {
      ready: false,
      error: formatErrorMessage(error),
      status: null
    }
  }
}

function buildRuntimeCard(key, entry = {}) {
  let detail = null

  if (key === 'ai') {
    const detailParts = []
    if (entry.provider) {
      detailParts.push(entry.provider)
    }
    if (entry.model) {
      detailParts.push(entry.model)
    }
    detailParts.push(entry.enabled ? 'enabled' : 'disabled')
    if (Array.isArray(entry.missingConfig) && entry.missingConfig.length > 0) {
      detailParts.push(`missing:${entry.missingConfig.join(',')}`)
    }
    detail = detailParts.join(' · ')
  } else if (entry.error) {
    detail = entry.error
  } else if (entry.status != null && entry.url) {
    detail = `${entry.status} · ${entry.url}`
  } else if (entry.url) {
    detail = entry.url
  } else if (entry.provider) {
    detail = entry.provider
  }

  return {
    key,
    label: runtimeCardLabels[key] ?? key,
    ready: Boolean(entry.ready),
    required: Boolean(entry.required),
    detail
  }
}

export function buildRuntimeStatusPayload(status = {}) {
  const cards = ['db', 'communityApp', 'imCore', 'ai'].map((key) =>
    buildRuntimeCard(key, status?.readiness?.[key])
  )
  const requiredCards = cards.filter((card) => card.required)

  return {
    ...status,
    summary: {
      status: status?.ok ? 'ready' : 'attention',
      readyCount: cards.filter((card) => card.ready).length,
      totalCount: cards.length,
      requiredReadyCount: requiredCards.filter((card) => card.ready).length,
      requiredCount: requiredCards.length
    },
    cards
  }
}

export function createRuntimeStatusService({
  config,
  db,
  fetchImpl = globalThis.fetch,
  aiConfigRepository = null,
  probeUrl = (url) => defaultProbeUrl(url, { fetchImpl })
} = {}) {
  return {
    async getStatus() {
      const serviceName = config?.serviceName || 'mock-data-studio'
      const communityAppUrl = buildHealthUrl(config?.upstreams?.communityAppBaseUrl)
      const imCoreUrl = buildHealthUrl(config?.upstreams?.imCoreBaseUrl)

      const dbReady = async () => {
        if (!db?.query) {
          return {
            ready: false,
            required: true,
            error: 'db is unavailable'
          }
        }

        try {
          await db.query('select 1 as ok')
          return {
            ready: true,
            required: true
          }
        } catch (error) {
          return {
            ready: false,
            required: true,
            error: formatErrorMessage(error)
          }
        }
      }

      const upstreamReady = async (url) => {
        if (!url) {
          return {
            ready: false,
            required: true,
            error: 'upstream URL is not configured',
            status: null,
            url: null
          }
        }

        try {
          const result = await probeUrl(url)
          return {
            ready: Boolean(result.ready),
            required: true,
            status: result.status ?? null,
            url,
            ...(result.error ? { error: result.error } : {})
          }
        } catch (error) {
          return {
            ready: false,
            required: true,
            status: null,
            url,
            error: formatErrorMessage(error)
          }
        }
      }

      const [dbStatus, communityAppStatus, imCoreStatus] = await Promise.all([
        dbReady(),
        upstreamReady(communityAppUrl),
        upstreamReady(imCoreUrl)
      ])

      let aiStatus = {
        provider: config?.ai?.provider || 'openai',
        model: config?.ai?.model || null,
        enabled: Boolean(config?.ai?.enabled),
        missingConfig: Array.isArray(config?.ai?.missingConfig) ? config.ai.missingConfig : [],
        required: false,
        ready: Boolean(config?.ai?.ready)
      }

      if (aiConfigRepository) {
        try {
          const dbAiConfig = await aiConfigRepository.getActive()
          if (dbAiConfig) {
            const detailParts = [dbAiConfig.provider, dbAiConfig.model]
            if (dbAiConfig.baseUrl) {
              detailParts.push(dbAiConfig.baseUrl)
            }
            detailParts.push(dbAiConfig.enabled ? 'enabled' : 'disabled')

            aiStatus = {
              provider: dbAiConfig.provider,
              model: dbAiConfig.model,
              baseUrl: dbAiConfig.baseUrl,
              enabled: dbAiConfig.enabled,
              required: false,
              ready: true,
              detail: detailParts.join(' · ')
            }
          }
        } catch {
        }
      }

      return {
        ok: [dbStatus, communityAppStatus, imCoreStatus].every((entry) => entry.ready),
        service: serviceName,
        readiness: {
          db: dbStatus,
          communityApp: communityAppStatus,
          imCore: imCoreStatus,
          ai: aiStatus
        }
      }
    }
  }
}

export function buildRuntimeStatusRouter({ runtimeStatusService } = {}) {
  if (!runtimeStatusService?.getStatus) {
    throw new Error('runtimeStatusService.getStatus is required')
  }

  const router = Router()

  router.get('/', asyncHandler(async (_req, res) => {
    const status = await runtimeStatusService.getStatus()
    res.json(buildRuntimeStatusPayload(status))
  }))

  return router
}
