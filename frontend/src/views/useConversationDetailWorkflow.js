import { computed, nextTick, reactive, ref, unref, watch } from 'vue'
import { listImConversationHistory, listImConversationMessages, markImConversationRead } from '../api/services/imCoreChatService'
import { imRealtimeClient } from '../im/imRealtimeClient'
import { useAuthStore } from '../stores/auth'
import { showToast } from '../ui/toastService'
import { createLatestRequestTracker } from '../utils/latestRequest'
import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'
import {
  advanceConversationSeqWaterline,
  commitPendingConversationMessage,
  createPendingConversationMessage,
  failPendingConversationMessage,
  findLatestConversationSeq,
  mapConversationMessage,
  mergeConversationMessages,
  parseConversationTargetId
} from './conversationDetailState'

/** @typedef {Record<string, any>} ConversationMessage */
/** @typedef {number | { token: number, scope: unknown }} RequestToken */
/** @typedef {{ scope: string, conversationId: string, meId: string, targetId: string }} ConversationViewContext */
/** @typedef {{ token: RequestToken, context: ConversationViewContext, messages: ConversationMessage[] }} LatestLoadBuffer */
/** @typedef {'idle' | 'loading-baseline' | 'awaiting-baseline' | 'backfilling'} HistoryFlowPhase */
/** @typedef {{ generation: number, scope: string, round: number, promise: Promise<void> }} HistoryBaselineRun */
/** @typedef {{ generation: number, context: ConversationViewContext, promise: Promise<void> | null }} HistoryBackfillRun */

/**
 * @param {{ conversationId: import('vue').MaybeRef<unknown>, chatArea: import('vue').Ref<HTMLElement | null> }} options
 */
export function useConversationDetailWorkflow({ conversationId: conversationIdSource, chatArea }) {
  const auth = useAuthStore()
  const loading = ref(false)
  const loadingHistory = ref(false)
  const items = ref(/** @type {ConversationMessage[]} */ ([]))
  const nextBeforeSeq = ref(/** @type {number | null} */ (null))
  const hasMoreHistory = ref(false)
  const error = ref('')
  const content = ref('')
  const sending = ref(false)
  const realtimeState = ref({ ...imRealtimeClient.state })
  const pendingClientMsgIds = new Set(/** @type {string[]} */ ([]))
  const loadRequestTracker = createLatestRequestTracker()
  const unsubscribers = /** @type {Array<() => void>} */ ([])
  /** @type {(() => void) | null} */
  let stopScopeWatch = null
  /** @type {LatestLoadBuffer | null} */
  let latestLoadBuffer = null
  let mounted = false

  const conversationId = computed(() => String(unref(conversationIdSource) || '').trim())
  const meId = computed(() => normalizeOpaqueId(auth.userId))
  const targetId = computed(() => parseConversationTargetId(conversationId.value, meId.value))
  const realtimeReady = computed(() => realtimeState.value.authed === true)
  const realtimeStatusText = computed(() => {
    if (realtimeState.value.authed) return '实时已就绪'
    if (realtimeState.value.connected) return '实时认证中'
    return '实时未连接'
  })
  const messages = computed(() => items.value.map(({
    messageIdentity: _messageIdentity,
    requestId: _requestId,
    ...message
  }) => ({
    ...message,
    timeLabel: new Date(message.createTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  })))
  const canSend = computed(() => auth.authed && Boolean(targetId.value))
  const historyFlow = {
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

  function currentViewScope() {
    return `${auth.tokenGeneration}:${meId.value}:${conversationId.value}`
  }

  function captureViewContext() {
    return {
      scope: currentViewScope(),
      conversationId: conversationId.value,
      meId: meId.value,
      targetId: targetId.value
    }
  }

  function resetHistoryFlow() {
    historyFlow.generation += 1
    historyFlow.scope = currentViewScope()
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
   * @param {RequestToken} token
   * @param {ConversationViewContext} context
   */
  function isCurrentRequest(token, context) {
    return loadRequestTracker.isCurrent(token) && currentViewScope() === context.scope
  }

  function scrollToBottom(viewScope = currentViewScope()) {
    nextTick(() => {
      if (currentViewScope() === viewScope && chatArea.value) {
        chatArea.value.scrollTop = chatArea.value.scrollHeight
      }
    })
  }

  function refresh() {
    const context = captureViewContext()
    const running = loadLatestHistory(context)
    const canEstablishBaseline = auth.authed && context.conversationId && context.meId && context.targetId
    if (historyFlow.waterline == null && canEstablishBaseline) {
      const baselineRun = {
        generation: historyFlow.generation,
        scope: context.scope,
        round: historyFlow.baseline.round + 1,
        promise: running
      }
      historyFlow.baseline.round = baselineRun.round
      historyFlow.baseline.run = baselineRun
      historyFlow.phase = 'loading-baseline'
      void running.finally(() => {
        if (historyFlow.baseline.run !== baselineRun) return
        historyFlow.baseline.run = null
        if (historyFlow.activeRun == null) historyFlow.phase = 'idle'
      })
    }
    return running
  }

  /** @param {ConversationViewContext} context */
  async function loadLatestHistory(context) {
    const token = loadRequestTracker.begin()
    if (!auth.authed || !context.conversationId || !context.meId || !context.targetId) {
      loading.value = false
      return
    }
    const bufferedMessages = /** @type {ConversationMessage[]} */ ([])
    latestLoadBuffer = { token, context, messages: bufferedMessages }
    error.value = ''
    loadingHistory.value = false
    loading.value = true
    try {
      const response = await listImConversationHistory(context.conversationId, { limit: 50 })
      if (!isCurrentRequest(token, context)) return
      const historyMessages = (Array.isArray(response?.items) ? response.items : []).map(mapConversationMessage)
      historyFlow.waterline = historyFlow.waterline == null
        ? findLatestConversationSeq(historyMessages)
        : advanceConversationSeqWaterline(historyFlow.waterline, historyMessages)
      const localDeliveryMessages = items.value.filter((message) =>
        message?.deliveryState === 'pending' || message?.deliveryState === 'failed'
      )
      items.value = mergeConversationMessages([], [
        ...historyMessages,
        ...bufferedMessages,
        ...localDeliveryMessages
      ])
      if (latestLoadBuffer?.token === token) latestLoadBuffer = null
      nextBeforeSeq.value = response?.nextBeforeSeq ?? null
      hasMoreHistory.value = Boolean(response?.hasMore && nextBeforeSeq.value != null)

      const maxSeq = findLatestConversationSeq(items.value)
      if (maxSeq > 0) {
        try { await markImConversationRead(context.conversationId, maxSeq) } catch {}
      }
      if (isCurrentRequest(token, context)) scrollToBottom(context.scope)
    } catch (cause) {
      if (isCurrentRequest(token, context)) error.value = cause?.message || '加载失败'
    } finally {
      if (latestLoadBuffer?.token === token) latestLoadBuffer = null
      if (isCurrentRequest(token, context)) loading.value = false
    }
  }

  async function loadEarlier() {
    if (loading.value || loadingHistory.value || !hasMoreHistory.value || nextBeforeSeq.value == null) return

    const token = loadRequestTracker.begin()
    const context = captureViewContext()
    if (!auth.authed || !context.conversationId || !context.meId || !context.targetId) return
    const previousHeight = chatArea.value?.scrollHeight || 0
    const previousTop = chatArea.value?.scrollTop || 0
    const beforeSeq = nextBeforeSeq.value
    loadingHistory.value = true
    error.value = ''
    try {
      const response = await listImConversationHistory(context.conversationId, { beforeSeq, limit: 50 })
      if (!isCurrentRequest(token, context)) return

      const historyMessages = (Array.isArray(response?.items) ? response.items : []).map(mapConversationMessage)
      items.value = mergeConversationMessages(items.value, historyMessages)
      nextBeforeSeq.value = response?.nextBeforeSeq ?? null
      hasMoreHistory.value = Boolean(response?.hasMore && nextBeforeSeq.value != null)

      await nextTick()
      if (isCurrentRequest(token, context) && chatArea.value) {
        chatArea.value.scrollTop = previousTop + (chatArea.value.scrollHeight - previousHeight)
      }
    } catch (cause) {
      if (isCurrentRequest(token, context)) error.value = cause?.message || '加载更早消息失败'
    } finally {
      if (isCurrentRequest(token, context)) loadingHistory.value = false
    }
  }

  async function send() {
    if (!content.value.trim() || !targetId.value) return

    sending.value = true
    try {
      if (!realtimeState.value.connected) throw new Error('IM 未连接')
      if (!realtimeState.value.authed) throw new Error('IM 正在认证，请稍后重试')
      const pendingContent = content.value
      const clientMsgId = imRealtimeClient.sendPrivateText({
        toUserId: targetId.value,
        content: pendingContent
      })
      if (clientMsgId) {
        pendingClientMsgIds.add(String(clientMsgId))
        items.value = mergeConversationMessages(items.value, [createPendingConversationMessage({
          clientMsgId,
          fromId: meId.value,
          toId: targetId.value,
          content: pendingContent
        })])
        scrollToBottom()
      }
      content.value = ''
    } catch (cause) {
      error.value = cause?.message || '发送失败'
    } finally {
      sending.value = false
    }
  }

  function resetForViewScope() {
    loadRequestTracker.invalidate()
    resetHistoryFlow()
    latestLoadBuffer = null
    loading.value = false
    loadingHistory.value = false
    items.value = []
    nextBeforeSeq.value = null
    hasMoreHistory.value = false
    error.value = ''
    content.value = ''
    sending.value = false
    pendingClientMsgIds.clear()
    if (auth.authed && conversationId.value && meId.value && targetId.value) refresh()
  }

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
    if (!auth.authed || !context.conversationId || !context.meId || !context.targetId) return
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

  async function handlePrivateMessage(rawMessage) {
    const context = captureViewContext()
    if (!auth.authed || !context.targetId || !rawMessage || rawMessage.conversationId !== context.conversationId) return
    const seq = Number(rawMessage?.seq || 0)
    const message = mapConversationMessage(rawMessage)
    const belongsToCurrentParticipants =
      (sameOpaqueId(message.fromId, context.meId) && sameOpaqueId(message.toId, context.targetId)) ||
      (sameOpaqueId(message.fromId, context.targetId) && sameOpaqueId(message.toId, context.meId))
    if (!belongsToCurrentParticipants || currentViewScope() !== context.scope) return

    if (latestLoadBuffer && isCurrentRequest(latestLoadBuffer.token, latestLoadBuffer.context)) {
      latestLoadBuffer.messages.push(message)
    }
    const previousMaxSeq = findLatestConversationSeq(items.value)
    const previousLength = items.value.length
    const mergedItems = mergeConversationMessages(items.value, [{ ...message, seq }])
    items.value = mergedItems
    const nextMaxSeq = findLatestConversationSeq(mergedItems)
    const isNewTail = mergedItems.length > previousLength && nextMaxSeq > previousMaxSeq
    if (isNewTail) scrollToBottom(context.scope)

    if (isNewTail && seq === nextMaxSeq && sameOpaqueId(message.toId, context.meId)) {
      try { await markImConversationRead(context.conversationId, seq) } catch {}
    }
  }

  function handleSendCommitted(message) {
    if (String(message?.cmd || '') !== 'sendPrivateText') return
    const clientMsgId = String(message?.clientMsgId || '')
    if (!clientMsgId || !pendingClientMsgIds.has(clientMsgId)) return
    const pending = items.value.find((item) =>
      item.clientMsgId === clientMsgId && sameOpaqueId(item.fromId, meId.value)
    )
    if (pending) {
      try {
        items.value = mergeConversationMessages(items.value, [commitPendingConversationMessage(pending, message)])
      } catch {
        // HTTP backfill remains authoritative when a committed frame is incomplete.
      }
    }
    pendingClientMsgIds.delete(clientMsgId)
  }

  function handleSendFailed(message) {
    if (String(message?.cmd || '') !== 'sendPrivateText') return
    const clientMsgId = String(message?.clientMsgId || '')
    if (!clientMsgId || !pendingClientMsgIds.has(clientMsgId)) return
    pendingClientMsgIds.delete(clientMsgId)
    items.value = items.value.map((item) =>
      item.clientMsgId === clientMsgId && sameOpaqueId(item.fromId, meId.value)
        ? failPendingConversationMessage(item)
        : item
    )

    const failureMessage = String(message?.message || '发送失败')
    error.value = failureMessage
    try {
      const traceId = String(message?.traceId || '')
      const traceSuffix = traceId ? ` (traceId=${traceId})` : ''
      showToast({ type: 'error', title: '发送失败', text: `${failureMessage}${traceSuffix}` })
    } catch {}
  }

  function subscribe(event, handler) {
    unsubscribers.push(imRealtimeClient.on(event, handler))
  }

  function mount() {
    if (mounted) return
    mounted = true
    resetHistoryFlow()
    realtimeState.value = { ...imRealtimeClient.state }
    stopScopeWatch = watch(currentViewScope, resetForViewScope)
    subscribe('privateMessage', handlePrivateMessage)
    subscribe('stateChanged', (state) => {
      const wasAuthed = realtimeState.value.authed === true
      realtimeState.value = { ...state }
      if (!wasAuthed && realtimeState.value.authed) void backfillAfterReconnect()
    })
    subscribe('sendCommitted', handleSendCommitted)
    subscribe('sendRejected', handleSendFailed)
    subscribe('sendError', handleSendFailed)
    if (auth.authed && conversationId.value && meId.value && targetId.value) refresh()
  }

  function unmount() {
    if (!mounted) return
    mounted = false
    stopScopeWatch?.()
    stopScopeWatch = null
    loadRequestTracker.invalidate()
    resetHistoryFlow()
    latestLoadBuffer = null
    pendingClientMsgIds.clear()
    for (const unsubscribe of unsubscribers.splice(0)) {
      try { unsubscribe?.() } catch {}
    }
  }

  const model = reactive({
    meId,
    targetId,
    realtimeReady,
    realtimeStatusText,
    loading,
    loadingHistory,
    messages,
    hasMoreHistory,
    error,
    content,
    sending,
    canSend
  })
  const actions = { refresh, loadEarlier, send }
  const lifecycle = { mount, unmount }

  return { model, actions, lifecycle }
}
