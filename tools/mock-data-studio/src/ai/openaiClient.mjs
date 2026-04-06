import OpenAI from 'openai'

function normalizeInputs(inputs = []) {
  return (Array.isArray(inputs) ? inputs : [])
    .map((input) => String(input ?? ''))
}

function parsePositiveInteger(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return fallback
  }
  return parsed
}

function extractResponseText(response = {}) {
  if (typeof response?.output_text === 'string' && response.output_text.trim() !== '') {
    return response.output_text.trim()
  }

  const output = Array.isArray(response?.output) ? response.output : []
  const textParts = []

  for (const block of output) {
    const contents = Array.isArray(block?.content) ? block.content : []
    for (const content of contents) {
      if (content?.type === 'output_text' && typeof content?.text === 'string') {
        textParts.push(content.text)
      }
    }
  }

  return textParts.join('\n').trim()
}

function parseOutputsFromText(text, expectedCount) {
  const normalized = String(text ?? '').trim()
  if (!normalized) {
    return null
  }

  const candidates = [normalized]
  const jsonBlock = normalized.match(/\{[\s\S]*\}/u)
  if (jsonBlock?.[0] && jsonBlock[0] !== normalized) {
    candidates.push(jsonBlock[0])
  }

  for (const candidate of candidates) {
    try {
      const parsed = JSON.parse(candidate)
      const outputs = Array.isArray(parsed?.outputs) ? parsed.outputs.map((item) => String(item ?? '')) : null
      if (outputs?.length === expectedCount) {
        return outputs
      }
    } catch {
      continue
    }
  }

  return null
}

function buildPrompt({ kind, inputs }) {
  const lines = inputs.map((input, index) => `${index + 1}. ${JSON.stringify(input)}`)
  return [
    '你是社区内容文案优化助手。',
    `任务类型：${kind}`,
    '请在不改变事实和含义的前提下，把下面每条文本改写得更自然、更像真实用户表达。',
    '要求：',
    '- 保留原始语义，不新增事实，不输出敏感内容。',
    '- 每条只返回一条改写结果，顺序必须一致。',
    '- 输出必须是严格 JSON：{"outputs":["...", "..."]}。',
    '',
    ...lines
  ].join('\n')
}

export function createOpenAiClient({
  config,
  dbConfig = null,
  OpenAIImpl = OpenAI
} = {}) {
  const provider = dbConfig?.provider ?? config?.ai?.provider ?? 'openai'
  const model = dbConfig?.model ?? config?.ai?.model ?? 'gpt-4.1-mini'
  const timeoutMs = parsePositiveInteger(dbConfig?.timeoutMs ?? config?.ai?.timeoutMs, 8000)
  const maxItemsPerJob = parsePositiveInteger(dbConfig?.maxItemsPerJob ?? config?.ai?.maxItemsPerJob, 20)
  const baseUrl = dbConfig?.baseUrl ?? null
  const apiKey = dbConfig?.apiKey ?? config?.ai?.apiKey ?? null
  const enabled = Boolean(dbConfig?.enabled ?? config?.ai?.enabled)

  const openaiClient = (apiKey || provider === 'ollama') ? (() => {
    const opts = {
      apiKey: apiKey || 'ollama',
      timeout: timeoutMs
    }
    if (baseUrl) {
      opts.baseURL = baseUrl
    }
    return new OpenAIImpl(opts)
  })() : null

  return {
    isConfigured() {
      return enabled && Boolean(openaiClient)
    },

    async enhanceTexts({ kind = 'generic', inputs = [], maxItems = maxItemsPerJob } = {}) {
      const normalizedInputs = normalizeInputs(inputs)
      const budget = Math.max(0, parsePositiveInteger(maxItems, maxItemsPerJob))

      if (normalizedInputs.length === 0) {
        return {
          outputs: [],
          usedCount: 0,
          skippedCount: 0,
          provider,
          model
        }
      }

      if (!enabled || !openaiClient) {
        const error = new Error('AI is not configured or not enabled')
        error.code = 'AI_NOT_CONFIGURED'
        throw error
      }

      if (budget === 0) {
        return {
          outputs: normalizedInputs,
          usedCount: 0,
          skippedCount: normalizedInputs.length,
          provider,
          model
        }
      }

      const selected = normalizedInputs.slice(0, budget)
      const skipped = normalizedInputs.slice(selected.length)
      const prompt = buildPrompt({ kind, inputs: selected })

      const response = await openaiClient.chat.completions.create({
        model,
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 4096
      })

      const responseText = response.choices?.[0]?.message?.content ?? ''
      const enhancedSelected = parseOutputsFromText(responseText, selected.length)

      if (!enhancedSelected) {
        const error = new Error('AI response format is invalid')
        error.code = 'AI_BAD_RESPONSE'
        throw error
      }

      return {
        outputs: [...enhancedSelected, ...skipped],
        usedCount: enhancedSelected.length,
        skippedCount: skipped.length,
        provider,
        model
      }
    }
  }
}
