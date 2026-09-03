import { listImConversationMessages, markImConversationRead } from '../api/services/imCoreChatService'
import { sameOpaqueId } from '../utils/opaqueId'
import {
  advanceConversationSeqWaterline,
  findLatestConversationSeq,
  mapConversationMessage,
  mergeConversationMessages
} from './conversationDetailState'

/** @typedef {Record<string, any>} ConversationMessage */
/** @typedef {{ scope: string, conversationId: string, meId: string, targetId: string }} ConversationViewContext */
/** @typedef {'idle' | 'loading-baseline' | 'awaiting-baseline' | 'backfilling'} HistoryFlowPhase */
/** @typedef {{ generation: number, scope: string, round: number, promise: Promise<void> }} HistoryBaselineRun */
/** @typedef {{ generation: number, context: ConversationViewContext, promise: Promise<void> | null }} HistoryBackfillRun */
/** @typedef {{ token: unknown, context: ConversationViewContext, messages: ConversationMessage[] }} LatestLoadBuffer */

export function createHistoryFlowState() {
  return {
    generation: 0,
    scope: '',
    phase: /** @type {HistoryFlowPhase} */ ('idle'),
    waterline: /** @type {number | null} */ (null),
    baseline: {
      round: 0,
      run: /** @type {HistoryBaselineRun | null} */ (null)
    },
    reconnect: {
      requestedRound: 0,
      completedRound: 0
    },
    backfillRound: 0,
    activeRun: /** @type {HistoryBackfillRun | null} */ (null)
  }
}

/**
 * @param {ReturnType<typeof createHistoryFlowState>} historyFlow
 * @param {string} scope
 */
export function resetHistoryFlowState(historyFlow, scope) {
  historyFlow.generation += 1
  historyFlow.scope = scope
  historyFlow.phase = 'idle'
  historyFlow.waterline = null
  historyFlow.baseline.round = 0
  historyFlow.baseline.run = null
  historyFlow.reconnect.requestedRound = 0
  historyFlow.reconnect.completedRound = 0
  historyFlow.backfillRound = 0
  historyFlow.activeRun = null
}

/**
 * @param {object} deps
 * @param {ReturnType<typeof createHistoryFlowState>} deps.historyFlow
 * @param {import('vue').Ref<ConversationMessage[]>} deps.items
 * @param {import('vue').Ref<string>} deps.error
 * @param {Set<string>} deps.pendingClientMsgIds
 * @param {() => string} deps.currentViewScope
 * @param {() => ConversationViewContext} deps.captureViewContext
 * @param {(context: ConversationViewContext) => boolean} deps.canLoadConversation
 * @param {() => Promise<void>} deps.refresh
 * @param {(token: unknown, context: ConversationViewContext) => boolean} deps.isCurrentRequest
 * @param {() => LatestLoadBuffer | null} deps.getLatestLoadBuffer
 * @param {(scope?: string) => void} deps.scrollToBottom
 */
export function createConversationHistoryBackfill(deps) {
  const {
    historyFlow,
    items,
    error,
    pendingClientMsgIds,
    currentViewScope,
    captureViewContext,
    canLoadConversation,
    refresh,
    isCurrentRequest,
    getLatestLoadBuffer,
    scrollToBottom
  } = deps

  /** @param {HistoryBackfillRun} run */
  function isCurrentHistoryRun(run) {
    return historyFlow.activeRun === run &&
      historyFlow.generation === run.generation &&
      historyFlow.scope === run.context.scope &&
      currentViewScope() === run.context.scope
  }

  /** @param {HistoryBackfillRun} run */
  async function awaitInitialHistoryBaseline(run) {
    if (historyFlow.waterline != null) return true

    let baselineRun = historyFlow.baseline.run
    if (!baselineRun) {
      void refresh()
      baselineRun = historyFlow.baseline.run
    }
    while (baselineRun &&
      baselineRun.generation === run.generation &&
      baselineRun.scope === run.context.scope &&
      isCurrentHistoryRun(run)) {
      historyFlow.phase = 'awaiting-baseline'
      await baselineRun.promise
      if (!isCurrentHistoryRun(run)) return false
      if (historyFlow.waterline != null) return true

      const replacementRun = historyFlow.baseline.run
      if (!replacementRun || replacementRun === baselineRun) return false
      baselineRun = replacementRun
    }
    return false
  }

  /** @param {HistoryBackfillRun} run */
  async function runBackfillPass(run) {
    try {
      if (!await awaitInitialHistoryBaseline(run) || !isCurrentHistoryRun(run)) return
      historyFlow.phase = 'backfilling'
      historyFlow.backfillRound += 1
      const { context } = run
      const previousMaxSeq = findLatestConversationSeq(items.value)
      const previousWaterline = Number(historyFlow.waterline)
      let afterSeq = Number(historyFlow.waterline)
      let receivedCount = 0

      while (true) {
        const response = await listImConversationMessages(context.conversationId, { afterSeq, limit: 100 })
        if (!isCurrentHistoryRun(run)) return
        const messages = (Array.isArray(response?.items) ? response.items : []).map(mapConversationMessage)
        receivedCount += messages.length
        const latestLoadBuffer = getLatestLoadBuffer()
        if (latestLoadBuffer && isCurrentRequest(latestLoadBuffer.token, latestLoadBuffer.context)) {
          latestLoadBuffer.messages.push(...messages)
        }
        items.value = mergeConversationMessages(items.value, messages)
        for (const message of messages) {
          if (message.clientMsgId && sameOpaqueId(message.fromId, context.meId)) {
            pendingClientMsgIds.delete(message.clientMsgId)
          }
        }

        const pageMaxSeq = findLatestConversationSeq(messages)
        const nextAfterSeq = advanceConversationSeqWaterline(afterSeq, messages)
        historyFlow.waterline = Math.max(Number(historyFlow.waterline ?? 0), nextAfterSeq)
        const hasInternalGap = pageMaxSeq > nextAfterSeq
        if (messages.length < 100 || nextAfterSeq <= afterSeq || hasInternalGap) break
        afterSeq = Math.max(nextAfterSeq, historyFlow.waterline)
      }

      const nextMaxSeq = findLatestConversationSeq(items.value)
      if (historyFlow.waterline > previousWaterline) {
        try { await markImConversationRead(context.conversationId, historyFlow.waterline) } catch {}
      }
      if (receivedCount > 0 && nextMaxSeq > previousMaxSeq) {
        if (isCurrentHistoryRun(run)) scrollToBottom(context.scope)
      }
    } catch (cause) {
      if (isCurrentHistoryRun(run)) error.value = cause?.message || '消息补同步失败，请手动刷新'
    }
  }

  function backfillAfterReconnect() {
    const context = captureViewContext()
    if (!canLoadConversation(context)) return
    if (historyFlow.scope !== context.scope) return

    historyFlow.reconnect.requestedRound += 1
    const activeRun = historyFlow.activeRun
    if (activeRun && isCurrentHistoryRun(activeRun)) {
      return activeRun.promise
    }

    /** @type {HistoryBackfillRun} */
    const run = {
      generation: historyFlow.generation,
      context,
      promise: null
    }
    historyFlow.activeRun = run
    const running = (async () => {
      try {
        while (isCurrentHistoryRun(run) &&
          historyFlow.reconnect.completedRound < historyFlow.reconnect.requestedRound) {
          const coveredReconnectRound = historyFlow.reconnect.requestedRound
          await runBackfillPass(run)
          if (!isCurrentHistoryRun(run)) return
          historyFlow.reconnect.completedRound = coveredReconnectRound
        }
      } finally {
        if (historyFlow.activeRun === run) {
          historyFlow.activeRun = null
          historyFlow.phase = 'idle'
        }
      }
    })()
    run.promise = running
    return running
  }

  return { backfillAfterReconnect }
}
