import assert from 'node:assert/strict'
import test from 'node:test'

import { createAiContentEnhancer } from '../src/ai/aiContentEnhancer.mjs'
import { createOpenAiClient } from '../src/ai/openaiClient.mjs'

function createConfig(overrides = {}) {
  return {
    ai: {
      provider: 'openai',
      enabled: true,
      apiKey: 'test-key',
      model: 'gpt-4.1-mini',
      timeoutMs: 8000,
      maxItemsPerJob: 2,
      missingConfig: [],
      ready: true,
      ...overrides
    }
  }
}

test('openai client caps provider inputs to the configured budget', async () => {
  const calls = []

  class FakeOpenAI {
    constructor(options) {
      this.options = options
      this.responses = {
        create: async (payload) => {
          calls.push({
            options: this.options,
            payload
          })
          return {
            output_text: JSON.stringify({
              outputs: ['AI::first', 'AI::second']
            })
          }
        }
      }
    }
  }

  const client = createOpenAiClient({
    config: createConfig({
      maxItemsPerJob: 2,
      timeoutMs: 9000
    }),
    OpenAIImpl: FakeOpenAI
  })

  const result = await client.enhanceTexts({
    kind: 'post-content',
    inputs: ['first', 'second', 'third']
  })

  assert.equal(calls.length, 1)
  assert.equal(calls[0].options.apiKey, 'test-key')
  assert.equal(calls[0].options.timeout, 9000)
  assert.match(calls[0].payload.input, /first/u)
  assert.match(calls[0].payload.input, /second/u)
  assert.doesNotMatch(calls[0].payload.input, /third/u)
  assert.deepEqual(result, {
    outputs: ['AI::first', 'AI::second', 'third'],
    usedCount: 2,
    skippedCount: 1,
    provider: 'openai',
    model: 'gpt-4.1-mini'
  })
})

test('openai client rejects enhancement when API key is missing', async () => {
  const client = createOpenAiClient({
    config: createConfig({
      apiKey: null,
      ready: false,
      missingConfig: ['apiKey']
    }),
    OpenAIImpl: class FakeOpenAI {}
  })

  await assert.rejects(
    () =>
      client.enhanceTexts({
        kind: 'comment-content',
        inputs: ['plain comment']
      }),
    /OpenAI API key is not configured/
  )
})

test('ai content enhancer keeps a per-run budget across multiple calls', async () => {
  class FakeOpenAI {
    constructor() {
      this.responses = {
        create: async (payload) => {
          const text = String(payload.input)
          const outputs = [...text.matchAll(/\d+\. "([^"]*)"/gu)].map((match) => `AI::${match[1]}`)
          return {
            output_text: JSON.stringify({ outputs })
          }
        }
      }
    }
  }

  const enhancer = createAiContentEnhancer({
    config: createConfig({
      maxItemsPerJob: 2
    }),
    aiClient: createOpenAiClient({
      config: createConfig({
        maxItemsPerJob: 2
      }),
      OpenAIImpl: FakeOpenAI
    })
  })

  const runOptions = {
    mode: 'manual-generate',
    aiEnhancement: true
  }

  const first = await enhancer.enhanceTextsForRun({
    kind: 'post-title',
    inputs: ['first title'],
    runOptions
  })
  const second = await enhancer.enhanceTextsForRun({
    kind: 'comment-content',
    inputs: ['comment one', 'comment two'],
    runOptions
  })

  assert.deepEqual(first.outputs, ['AI::first title'])
  assert.deepEqual(second.outputs, ['AI::comment one', 'comment two'])
  assert.equal(first.usedCount, 1)
  assert.equal(second.usedCount, 1)
  assert.equal(second.skippedCount, 1)
})

test('ai content enhancer falls back to rule-generated text when provider fails', async () => {
  const enhancer = createAiContentEnhancer({
    config: createConfig(),
    aiClient: {
      async enhanceTexts() {
        throw new Error('provider timeout')
      }
    }
  })

  const result = await enhancer.enhanceTextsForRun({
    kind: 'message-content',
    inputs: ['hello world'],
    runOptions: {
      mode: 'manual-generate',
      aiEnhancement: true
    }
  })

  assert.deepEqual(result.outputs, ['hello world'])
  assert.equal(result.applied, false)
  assert.equal(result.reason, 'ai_provider_error')
  assert.equal(result.errorMessage, 'provider timeout')
})
