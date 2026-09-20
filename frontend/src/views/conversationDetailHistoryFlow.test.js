import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversationMessages: vi.fn(),
  markImConversationRead: vi.fn().mockResolvedValue({})
}))

import { listImConversationMessages, markImConversationRead } from '../api/services/imCoreChatService'
import {
  createConversationHistoryBackfill,
  createHistoryFlowState,
  resetHistoryFlowState
} from './conversationDetailHistoryFlow'
import { ref } from 'vue'

const ME_ID = '11111111-1111-7111-8111-111111111111'
const TARGET_ID = '22222222-2222-7222-8222-222222222222'
const CONVERSATION_ID = `${ME_ID}_${TARGET_ID}`
const SCOPE = `1:${ME_ID}:${CONVERSATION_ID}`

function context(overrides = {}) {
  return { scope: SCOPE, conversationId: CONVERSATION_ID, meId: ME_ID, targetId: TARGET_ID, ...overrides }
}

function rawMessage(seq, { fromUserId = TARGET_ID, clientMsgId = '' } = {}) {
  return {
    messageId: `00000000-0000-7000-8000-${String(seq).padStart(12, '0')}`,
    seq,
    fromUserId,
    toUserId: fromUserId === ME_ID ? TARGET_ID : ME_ID,
    content: `msg-${seq}`,
    clientMsgId,
    createdAtEpochMs: 1_700_000_000_000 + seq
  }
}

function mappedMessages(raw) {
  // 与 workflow 相同的映射：HTTP 页响应经 mapConversationMessage 归一。
  return import('./conversationDetailState').then(({ mapConversationMessage }) =>
    raw.map((item) => mapConversationMessage(item))
  )
}

function createDeps({
  canLoad = true,
  latestLoadBuffer = null,
  isCurrentRequest = () => true,
  items = ref([]),
  baselineWaterline = 0
} = {}) {
  const error = ref('')
  const pendingClientMsgIds = new Set()
  const scrollToBottom = vi.fn()
  const historyFlow = createHistoryFlowState()
  const mutableScope = { value: SCOPE }
  resetHistoryFlowState(historyFlow, SCOPE)
  const refresh = vi.fn(() => {
    const baselineRun = {
      generation: historyFlow.generation,
      scope: mutableScope.value,
      round: historyFlow.baseline.round + 1,
      promise: Promise.resolve().then(() => {
        if (baselineWaterline != null) historyFlow.waterline = baselineWaterline
      })
    }
    historyFlow.baseline.round = baselineRun.round
    historyFlow.baseline.run = baselineRun
    return baselineRun.promise
  })

  const deps = {
    historyFlow,
    items,
    error,
    pendingClientMsgIds,
    currentViewScope: () => mutableScope.value,
    captureViewContext: () => context({ scope: mutableScope.value }),
    canLoadConversation: canLoad ? () => true : () => false,
    refresh,
    isCurrentRequest,
    getLatestLoadBuffer: () => latestLoadBuffer,
    scrollToBottom
  }
  const flow = createConversationHistoryBackfill(deps)
  return { flow, deps, historyFlow, error, items, pendingClientMsgIds, refresh, scrollToBottom, mutableScope }
}

describe('createHistoryFlowState', () => {
  it('resets all flow state and advances the generation on conversation switch', () => {
    const historyFlow = createHistoryFlowState()
    historyFlow.phase = 'backfilling'
    historyFlow.waterline = 7
    historyFlow.backfillRound = 3
    const previousGeneration = historyFlow.generation

    resetHistoryFlowState(historyFlow, 'new-scope')

    expect(historyFlow.generation).toBe(previousGeneration + 1)
    expect(historyFlow.scope).toBe('new-scope')
    expect(historyFlow.phase).toBe('idle')
    expect(historyFlow.waterline).toBe(null)
    expect(historyFlow.baseline.round).toBe(0)
    expect(historyFlow.baseline.run).toBe(null)
    expect(historyFlow.reconnect.requestedRound).toBe(0)
    expect(historyFlow.reconnect.completedRound).toBe(0)
    expect(historyFlow.backfillRound).toBe(0)
    expect(historyFlow.activeRun).toBe(null)
  })
})

describe('createConversationHistoryBackfill', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    markImConversationRead.mockResolvedValue({})
  })

  it('backfills messages after the requested seq and marks the new waterline read', async () => {
    listImConversationMessages.mockResolvedValueOnce({ items: [rawMessage(1), rawMessage(2)] })
    const { flow, historyFlow, items, scrollToBottom } = createDeps()

    await flow.backfillAfterReconnect()

    expect(listImConversationMessages).toHaveBeenCalledWith(CONVERSATION_ID, { afterSeq: 0, limit: 100 })
    expect(items.value).toHaveLength(2)
    expect(historyFlow.waterline).toBe(2)
    expect(markImConversationRead).toHaveBeenCalledWith(CONVERSATION_ID, 2)
    expect(scrollToBottom).toHaveBeenCalledWith(SCOPE)
    expect(historyFlow.phase).toBe('idle')
  })

  it('keeps paging while full pages come back and the seq advances without internal gaps', async () => {
    const fullPage = Array.from({ length: 100 }, (_, index) => rawMessage(index + 1))
    const tailPage = [rawMessage(101)]
    listImConversationMessages
      .mockResolvedValueOnce({ items: fullPage })
      .mockResolvedValueOnce({ items: tailPage })
    const { flow, historyFlow, items } = createDeps()

    await flow.backfillAfterReconnect()

    expect(listImConversationMessages.mock.calls.map(([, request]) => request.afterSeq)).toEqual([0, 100])
    expect(items.value).toHaveLength(101)
    expect(historyFlow.waterline).toBe(101)
  })

  it('stops at an internal gap and resumes from the new waterline on the next reconnect', async () => {
    listImConversationMessages.mockResolvedValueOnce({ items: [rawMessage(3), rawMessage(5)] })
    const { flow, historyFlow, items } = createDeps({ baselineWaterline: 2 })

    await flow.backfillAfterReconnect()

    // 页内缺口（缺 4）：水位停在 3，缺口之后的消息仍进列表但不提前标读。
    expect(items.value).toHaveLength(2)
    expect(historyFlow.waterline).toBe(3)
    expect(markImConversationRead).toHaveBeenCalledWith(CONVERSATION_ID, 3)

    listImConversationMessages.mockResolvedValueOnce({ items: [rawMessage(4), rawMessage(5)] })
    await flow.backfillAfterReconnect()
    expect(listImConversationMessages.mock.calls.map(([, request]) => request.afterSeq)).toEqual([2, 3])
    expect(markImConversationRead).toHaveBeenLastCalledWith(CONVERSATION_ID, 5)
  })

  it('removes own pending sends from the pending set when backfill proves them persisted', async () => {
    const messages = [rawMessage(4, { fromUserId: ME_ID, clientMsgId: 'client-echo' })]
    listImConversationMessages.mockResolvedValueOnce({ items: messages })
    const { flow, pendingClientMsgIds } = createDeps()
    pendingClientMsgIds.add('client-echo')

    await flow.backfillAfterReconnect()

    expect(pendingClientMsgIds.has('client-echo')).toBe(false)
  })

  it('leaves peer pending clientMsgIds alone even if the id collides', async () => {
    const messages = [rawMessage(4, { fromUserId: TARGET_ID, clientMsgId: 'client-echo' })]
    listImConversationMessages.mockResolvedValueOnce({ items: messages })
    const { flow, pendingClientMsgIds } = createDeps()
    pendingClientMsgIds.add('client-echo')

    await flow.backfillAfterReconnect()

    // clientMsgId 的唯一性是发送者作用域：对方使用相同值不能移除我的 pending。
    expect(pendingClientMsgIds.has('client-echo')).toBe(true)
  })

  it('feeds realtime messages arriving during an in-flight latest load through the load buffer', async () => {
    let resolveBackfillPage
    listImConversationMessages.mockImplementationOnce(() => new Promise((resolve) => {
      resolveBackfillPage = resolve
    }))
    const buffer = { token: 'load-token', context: context(), messages: [] }
    const { flow } = createDeps({ latestLoadBuffer: buffer, isCurrentRequest: () => true })

    const pending = flow.backfillAfterReconnect()
    await vi.waitFor(() => expect(typeof resolveBackfillPage).toBe('function'))
    resolveBackfillPage({ items: [rawMessage(1)] })
    await pending

    expect(buffer.messages).toHaveLength(1)
  })

  it('queues one more pass when a reconnect lands while a pass is in flight', async () => {
    let resolveFirstPage
    listImConversationMessages
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirstPage = resolve }))
      .mockResolvedValueOnce({ items: [rawMessage(5)] })
    const { flow } = createDeps()

    const firstRun = flow.backfillAfterReconnect()
    const secondRun = flow.backfillAfterReconnect()
    expect(secondRun).toBe(firstRun)

    await vi.waitFor(() => expect(typeof resolveFirstPage).toBe('function'))
    // 第一轮连续推进到 4；第二轮从新水位 4 起补，把水位推进到 5。
    resolveFirstPage({ items: [rawMessage(1), rawMessage(2), rawMessage(3), rawMessage(4)] })
    await firstRun

    expect(listImConversationMessages.mock.calls.map(([, request]) => request.afterSeq)).toEqual([0, 4])
  })

  it('discards the run when the view scope switches conversations mid-flight', async () => {
    let resolveStale
    listImConversationMessages.mockImplementationOnce(() => new Promise((resolve) => { resolveStale = resolve }))
    const { flow, items, mutableScope } = createDeps()

    const pending = flow.backfillAfterReconnect()
    await vi.waitFor(() => expect(typeof resolveStale).toBe('function'))
    // 路由切换会话：scope 变化使在途回填失效，迟到的页面不得写回列表。
    mutableScope.value = `1:${ME_ID}:other-conversation`
    resolveStale({ items: [rawMessage(1)] })
    await pending

    expect(items.value).toHaveLength(0)
  })

  it('does not start when the view cannot load the conversation', async () => {
    const { flow, historyFlow } = createDeps({ canLoad: false })

    await flow.backfillAfterReconnect()

    expect(listImConversationMessages).not.toHaveBeenCalled()
    expect(historyFlow.phase).toBe('idle')
  })

  it('does not start when the history flow belongs to a different scope', async () => {
    const historyFlow = createHistoryFlowState()
    resetHistoryFlowState(historyFlow, 'other-scope')
    const error = ref('')
    const flow = createConversationHistoryBackfill({
      historyFlow,
      items: ref([]),
      error,
      pendingClientMsgIds: new Set(),
      currentViewScope: () => SCOPE,
      captureViewContext: () => context(),
      canLoadConversation: () => true,
      refresh: vi.fn(),
      isCurrentRequest: () => true,
      getLatestLoadBuffer: () => null,
      scrollToBottom: vi.fn()
    })

    await flow.backfillAfterReconnect()

    expect(listImConversationMessages).not.toHaveBeenCalled()
  })

  it('surfaces a backfill failure in error without corrupting committed items', async () => {
    const initial = await mappedMessages([rawMessage(1), rawMessage(2)])
    listImConversationMessages.mockRejectedValueOnce(new Error('补同步失败'))
    const items = ref(initial)
    const { flow, error } = createDeps({ items })

    await flow.backfillAfterReconnect()

    expect(error.value).toBe('补同步失败')
    expect(items.value).toHaveLength(2)
  })

  it('waits for the initial latest-history baseline before the first pass', async () => {
    let resolveBaseline
    const baselinePromise = new Promise((resolve) => { resolveBaseline = resolve })
    const historyFlow = createHistoryFlowState()
    resetHistoryFlowState(historyFlow, SCOPE)
    // workflow 的 refresh() 建立 baseline.run，promise 完成后 waterline 仍是 null 会阻断。
    const refresh = vi.fn(() => {
      historyFlow.baseline.run = {
        generation: historyFlow.generation,
        scope: SCOPE,
        round: 1,
        promise: baselinePromise
      }
      return baselinePromise
    })
    const items = ref([])
    const flow = createConversationHistoryBackfill({
      historyFlow,
      items,
      error: ref(''),
      pendingClientMsgIds: new Set(),
      currentViewScope: () => SCOPE,
      captureViewContext: () => context(),
      canLoadConversation: () => true,
      refresh,
      isCurrentRequest: () => true,
      getLatestLoadBuffer: () => null,
      scrollToBottom: vi.fn()
    })

    const pending = flow.backfillAfterReconnect()
    await Promise.resolve()
    // baseline 未完成：不发起 backfill 请求。
    expect(listImConversationMessages).not.toHaveBeenCalled()

    historyFlow.waterline = 3
    resolveBaseline()
    await pending
    expect(listImConversationMessages).toHaveBeenCalledWith(CONVERSATION_ID, { afterSeq: 3, limit: 100 })
  })

  it('only scrolls when the backfill grew the visible tail', async () => {
    const initial = await mappedMessages([rawMessage(1), rawMessage(2)])
    listImConversationMessages.mockResolvedValueOnce({ items: [rawMessage(2), rawMessage(3)] })
    const items = ref(initial)
    const { flow, scrollToBottom } = createDeps({ items })

    await flow.backfillAfterReconnect()

    expect(scrollToBottom).toHaveBeenCalledTimes(1)
  })
})
