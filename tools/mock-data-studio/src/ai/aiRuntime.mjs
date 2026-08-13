import { createAiContentEnhancer } from './aiContentEnhancer.mjs'
import { createOpenAiClient } from './openaiClient.mjs'

function hasText(value) {
  return typeof value === 'string' && value.trim() !== ''
}

export function resolveAiRuntimeConfig({ config, dbConfig = null } = {}) {
  const envConfig = config?.ai ?? {}
  const source = dbConfig ?? envConfig
  const provider = source.provider ?? 'openai'
  const enabled = Boolean(source.enabled)
  const apiKey = source.apiKey ?? null
  const missingConfig = dbConfig
    ? []
    : Array.isArray(source.missingConfig) ? [...source.missingConfig] : []
  if (dbConfig && enabled && provider !== 'ollama' && !hasText(apiKey)) missingConfig.push('apiKey')

  return {
    provider,
    model: source.model ?? envConfig.model ?? null,
    baseUrl: source.baseUrl ?? null,
    apiKey,
    enabled,
    timeoutMs: source.timeoutMs ?? envConfig.timeoutMs ?? 8000,
    maxItemsPerJob: source.maxItemsPerJob ?? envConfig.maxItemsPerJob ?? 20,
    missingConfig,
    ready: dbConfig ? enabled && missingConfig.length === 0 : Boolean(source.ready)
  }
}

export async function loadAiRuntimeConfig({ config, aiConfigRepository = null } = {}) {
  const dbConfig = aiConfigRepository?.getActive ? await aiConfigRepository.getActive() : null
  return resolveAiRuntimeConfig({ config, dbConfig })
}

export function createRuntimeAiContentEnhancer({ config, aiConfigRepository } = {}) {
  const enhancersByRun = new WeakMap()

  async function createEnhancer() {
    const dbConfig = aiConfigRepository?.getActive ? await aiConfigRepository.getActive() : null
    const runtimeConfig = { ...config, ai: resolveAiRuntimeConfig({ config, dbConfig }) }
    return createAiContentEnhancer({
      config: runtimeConfig,
      aiClient: createOpenAiClient({ config: runtimeConfig, dbConfig }),
      dbAiConfig: dbConfig
    })
  }

  async function enhancerFor(runOptions) {
    if (!runOptions || typeof runOptions !== 'object') return createEnhancer()
    let promise = enhancersByRun.get(runOptions)
    if (!promise) {
      promise = createEnhancer()
      enhancersByRun.set(runOptions, promise)
    }
    return promise
  }

  return {
    async enhanceTextsForRun(request = {}) {
      try {
        return (await enhancerFor(request.runOptions)).enhanceTextsForRun(request)
      } catch (error) {
        return {
          outputs: (Array.isArray(request.inputs) ? request.inputs : []).map((input) => String(input ?? '')),
          applied: false,
          reason: 'ai_config_unavailable',
          errorMessage: error?.message ?? String(error)
        }
      }
    }
  }
}
