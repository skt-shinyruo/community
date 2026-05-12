// @vitest-environment node

import { beforeEach, describe, expect, it, vi } from 'vitest'

import imCoreHttp from '../imCoreHttp'
import { BusinessError } from '../result'
import {
  listImConversationMessages,
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

  it('markImConversationRead should reject raw object responses', async () => {
    imCoreHttp.post.mockResolvedValue({ data: {} })

    await expect(markImConversationRead('c1', 8)).rejects.toBeInstanceOf(BusinessError)
  })
})
