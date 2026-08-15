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
/** @typedef {{ scope: string, promise: Promise<void> }} InitialHistoryBaselineRun */
/** @typedef {{ token: RequestToken, context: ConversationViewContext, rerunRequested: boolean, promise: Promise<void> | null }} BackfillRun */

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
  const backfillRequestTracker = createLatestRequestTracker()
  const unsubscribers = /** @type {Array<() => void>} */ ([])
  /** @type {(() => void) | null} */
  let stopScopeWatch = null
  /** @type {LatestLoadBuffer | null} */
  let latestLoadBuffer = null
  /** @type {InitialHistoryBaselineRun | null} */
  let initialHistoryBaselineRun = null
  /** @type {BackfillRun | null} */
  let backfillRun = null
  /** @type {number | null} */
  let backfillWaterline = null
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
  const messages = computed(() => items.value.map((message) => ({
    ...message,
    timeLabel: new Date(message.createTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  })))
  const canSend = computed(() => auth.authed && Boolean(targetId.value))

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
    if (backfillWaterline == null && canEstablishBaseline) {
      const baselineRun = { scope: context.scope, promise: running }
      initialHistoryBaselineRun = baselineRun
      void running.finally(() => {
        if (initialHistoryBaselineRun === baselineRun) initialHistoryBaselineRun = null
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
      backfillWaterline = backfillWaterline == null
        ? findLatestConversationSeq(historyMessages)
        : advanceConversationSeqWaterline(backfillWaterline, historyMessages)
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
    backfillRequestTracker.invalidate()
    initialHistoryBaselineRun = null
    backfillRun = null
    backfillWaterline = null
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

  /** @param {BackfillRun} run */
  function isCurrentBackfill(run) {
    return backfillRequestTracker.isCurrent(run.token) && currentViewScope() === run.context.scope
  }

  /** @param {BackfillRun} run */
  async function awaitInitialHistoryBaseline(run) {
    if (backfillWaterline != null) return true

    let baselineRun = initialHistoryBaselineRun
    if (!baselineRun) {
      void refresh()
      baselineRun = initialHistoryBaselineRun
    }
    while (baselineRun && baselineRun.scope === run.context.scope && isCurrentBackfill(run)) {
      await baselineRun.promise
      if (!isCurrentBackfill(run)) return false
      if (backfillWaterline != null) return true

      const replacementRun = initialHistoryBaselineRun
      if (!replacementRun || replacementRun === baselineRun) return false
      baselineRun = replacementRun
    }
    return false
  }

  /** @param {BackfillRun} run */
  async function runBackfillPass(run) {
    try {
      if (!await awaitInitialHistoryBaseline(run) || !isCurrentBackfill(run)) return
      const { context } = run
      const previousMaxSeq = findLatestConversationSeq(items.value)
      let afterSeq = Number(backfillWaterline)
      let receivedCount = 0

      while (true) {
        const response = await listImConversationMessages(context.conversationId, { afterSeq, limit: 100 })
        if (!isCurrentBackfill(run)) return
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
        backfillWaterline = Math.max(Number(backfillWaterline ?? 0), nextAfterSeq)
        const hasInternalGap = pageMaxSeq > nextAfterSeq
        if (messages.length < 100 || nextAfterSeq <= afterSeq || hasInternalGap) break
        afterSeq = Math.max(nextAfterSeq, backfillWaterline)
      }

      const nextMaxSeq = findLatestConversationSeq(items.value)
      if (receivedCount > 0 && nextMaxSeq > previousMaxSeq) {
        try { await markImConversationRead(context.conversationId, nextMaxSeq) } catch {}
        if (isCurrentBackfill(run)) scrollToBottom(context.scope)
      }
    } catch (cause) {
      if (isCurrentBackfill(run)) error.value = cause?.message || '消息补同步失败，请手动刷新'
    }
  }

  function backfillAfterReconnect() {
    const context = captureViewContext()
    if (!auth.authed || !context.conversationId || !context.meId || !context.targetId) return
    if (backfillRun && backfillRun.context.scope === context.scope) {
      backfillRun.rerunRequested = true
      return backfillRun.promise
    }

    /** @type {BackfillRun} */
    const run = {
      token: backfillRequestTracker.begin(),
      context,
      rerunRequested: true,
      promise: null
    }
    const running = (async () => {
      try {
        while (run.rerunRequested && isCurrentBackfill(run)) {
          run.rerunRequested = false
          await runBackfillPass(run)
        }
      } finally {
        if (backfillRun === run) backfillRun = null
      }
    })()
    run.promise = running
    backfillRun = run
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
    backfillRequestTracker.invalidate()
    initialHistoryBaselineRun = null
    backfillRun = null
    backfillWaterline = null
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
