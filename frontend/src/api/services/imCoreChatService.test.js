// @vitest-environment node

import { beforeEach, describe, expect, it, vi } from 'vitest'

import imCoreHttp from '../imCoreHttp'
import { BusinessError } from '../result'
import {
  listImConversationHistory,
  listImConversationMessages,
  listImConversationPage,
  listImConversations,
  markImConversationRead
} from './imCoreChatService'

vi.mock('../imCoreHttp', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('api/services/imCoreChatService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listImConversations should unwrap Result body', async () => {
    imCoreHttp.get.mockResolvedValue({
      data: {
        code: 0,
        message: '',
        data: [{ conversationId: 'c1' }],
        traceId: 'trace-im'
      }
    })

    await expect(listImConversations({ page: 1, size: 2 })).resolves.toEqual([{ conversationId: 'c1' }])
    expect(imCoreHttp.get).toHaveBeenCalledWith('/api/im/conversations', { params: { page: 1, size: 2 } })
  })

  it('listImConversations should reject raw array responses', async () => {
    imCoreHttp.get.mockResolvedValue({ data: [{ conversationId: 'legacy' }] })

    await expect(listImConversations()).rejects.toBeInstanceOf(BusinessError)
  })

  it('listImConversationMessages should unwrap Result body', async () => {
    imCoreHttp.get.mockResolvedValue({
      data: {
        code: 0,
        message: '',
        data: { items: [{ seq: 1 }] },
        traceId: 'trace-im'
      }
    })

    await expect(listImConversationMessages('c1')).resolves.toEqual({ items: [{ seq: 1 }] })
  })

  it('listImConversationPage should unwrap Result body and send cursor pagination params', async () => {
    imCoreHttp.get.mockResolvedValue({
      data: {
        code: 0,
        message: '',
        data: { items: [{ conversationId: 'c1' }], nextCursor: 'next-1', hasMore: true },
        traceId: 'trace-page'
      }
    })

    await expect(listImConversationPage({ cursor: 'cursor-1', size: 3 })).resolves.toEqual({
      items: [{ conversationId: 'c1' }],
      nextCursor: 'next-1',
      hasMore: true
    })
    expect(imCoreHttp.get).toHaveBeenCalledWith('/api/im/conversations/page', {
      params: { cursor: 'cursor-1', size: 3 }
    })
  })

  it('listImConversationHistory should encode the conversation id and unwrap Result body', async () => {
    imCoreHttp.get.mockResolvedValue({
      data: {
        code: 0,
        message: '',
        data: {
          conversationId: 'c/1 ?x',
          items: [{ seq: 4 }],
          nextBeforeSeq: 4,
          hasMore: true,
          lastReadSeq: 2
        },
        traceId: 'trace-history'
      }
    })

    await expect(listImConversationHistory('c/1 ?x', { beforeSeq: 9, limit: 4 })).resolves.toEqual({
      conversationId: 'c/1 ?x',
      items: [{ seq: 4 }],
      nextBeforeSeq: 4,
      hasMore: true,
      lastReadSeq: 2
    })
    expect(imCoreHttp.get).toHaveBeenCalledWith('/api/im/conversations/c%2F1%20%3Fx/messages/history', {
      params: { beforeSeq: 9, limit: 4 }
    })
  })

  it('listImConversationHistory should omit the initial boundary while keeping the default limit', async () => {
    imCoreHttp.get.mockResolvedValue({
      data: {
        code: 0,
        message: '',
        data: { conversationId: 'c1', items: [], nextBeforeSeq: null, hasMore: false, lastReadSeq: 0 },
        traceId: 'trace-history-initial'
      }
    })

    await expect(listImConversationHistory('c1')).resolves.toMatchObject({ conversationId: 'c1', items: [] })
    expect(imCoreHttp.get).toHaveBeenCalledWith('/api/im/conversations/c1/messages/history', {
      params: { beforeSeq: undefined, limit: 50 }
    })
  })

  it('new IM pagination clients should reject responses without a Result envelope', async () => {
    imCoreHttp.get.mockResolvedValue({ data: { items: [] } })

    await expect(listImConversationPage()).rejects.toBeInstanceOf(BusinessError)
    await expect(listImConversationHistory('c1', { beforeSeq: 2 })).rejects.toBeInstanceOf(BusinessError)
  })

  it('markImConversationRead should reject raw object responses', async () => {
    imCoreHttp.post.mockResolvedValue({ data: {} })

    await expect(markImConversationRead('c1', 8)).rejects.toBeInstanceOf(BusinessError)
  })
})
