const usageByRunOptions = new WeakMap()

function normalizeRunOptions(runOptions = {}) {
  return {
    mode: runOptions?.mode ?? 'manual-generate',
    aiEnhancement: Boolean(runOptions?.aiEnhancement)
  }
}

function readUsedCount(runOptions) {
  if (runOptions && typeof runOptions === 'object') {
    return usageByRunOptions.get(runOptions) ?? 0
  }

  return 0
}

function writeUsedCount(runOptions, usedCount) {
  if (runOptions && typeof runOptions === 'object') {
    usageByRunOptions.set(runOptions, usedCount)
  }
}

function fallbackResult(inputs, reason, errorMessage = null) {
  return {
    outputs: inputs.map((input) => String(input ?? '')),
    applied: false,
    reason,
    ...(errorMessage ? { errorMessage } : {})
  }
}

function parsePositiveInteger(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return fallback
  }
  return parsed
}

function shouldUseAi({ config, runOptions }) {
  const normalized = normalizeRunOptions(runOptions)

  if (!config?.ai?.enabled) {
    return {
      useAi: false,
      reason: 'ai_disabled'
    }
  }

  if (normalized.mode !== 'manual-generate') {
    return {
      useAi: false,
      reason: 'mode_not_manual'
    }
  }

  if (!normalized.aiEnhancement) {
    return {
      useAi: false,
      reason: 'ai_toggle_off'
    }
  }

  if (!config?.ai?.ready) {
    return {
      useAi: false,
      reason: 'ai_not_ready'
    }
  }

  return {
    useAi: true,
    reason: null
  }
}

export function createAiContentEnhancer({
  config,
  aiClient = null
} = {}) {
  const maxItemsPerJob = parsePositiveInteger(config?.ai?.maxItemsPerJob, 20)

  return {
    async enhanceTextsForRun({ kind = 'generic', inputs = [], runOptions = null } = {}) {
      const normalizedInputs = (Array.isArray(inputs) ? inputs : []).map((input) => String(input ?? ''))
      if (normalizedInputs.length === 0) {
        return {
          outputs: [],
          applied: false,
          reason: 'empty_input'
        }
      }

      const decision = shouldUseAi({ config, runOptions })
      if (!decision.useAi) {
        return fallbackResult(normalizedInputs, decision.reason)
      }

      if (!aiClient?.enhanceTexts) {
        return fallbackResult(normalizedInputs, 'ai_client_unavailable')
      }

      const usedCount = readUsedCount(runOptions)
      const remainingBudget = Math.max(0, maxItemsPerJob - usedCount)

      if (remainingBudget === 0) {
        return fallbackResult(normalizedInputs, 'ai_budget_exhausted')
      }

      try {
        const result = await aiClient.enhanceTexts({
          kind,
          inputs: normalizedInputs,
          maxItems: remainingBudget
        })
        const enhancedOutputs = Array.isArray(result?.outputs) ? result.outputs.map((item) => String(item ?? '')) : null

        if (!enhancedOutputs || enhancedOutputs.length !== normalizedInputs.length) {
          return fallbackResult(normalizedInputs, 'ai_bad_output')
        }

        writeUsedCount(runOptions, usedCount + Number(result?.usedCount ?? 0))

        return {
          outputs: enhancedOutputs,
          applied: true,
          usedCount: Number(result?.usedCount ?? 0),
          skippedCount: Number(result?.skippedCount ?? 0),
          provider: result?.provider ?? config?.ai?.provider ?? 'openai',
          model: result?.model ?? config?.ai?.model ?? null
        }
      } catch (error) {
        return fallbackResult(normalizedInputs, 'ai_provider_error', error?.message ?? String(error))
      }
    }
  }
}
