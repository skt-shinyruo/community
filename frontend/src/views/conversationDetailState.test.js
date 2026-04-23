import { describe, expect, it } from 'vitest'

import {
  findLatestConversationSeq,
  mapConversationMessage,
  mergeConversationMessages,
  parseConversationTargetId
} from './conversationDetailState'

describe('conversationDetailState', () => {
  it('parses the other participant from a UUID conversation id', () => {
    const me = '11111111-1111-7111-8111-111111111111'
    const other = '22222222-2222-7222-8222-222222222222'

    expect(parseConversationTargetId(`${me}_${other}`, me)).toBe(other)
    expect(parseConversationTargetId(`${other}_${me}`, me)).toBe(other)
  })

  it('maps conversation messages with UUID sender and receiver ids', () => {
    expect(mapConversationMessage({
      seq: 12,
      messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      fromUserId: '11111111-1111-7111-8111-111111111111',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello',
      createdAtEpochMs: 123456789
    })).toEqual({
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 12,
      fromId: '11111111-1111-7111-8111-111111111111',
      toId: '22222222-2222-7222-8222-222222222222',
      content: 'hello',
      createTime: 123456789
    })
  })

  it('merges conversation messages without duplicating the same seq and keeps chronological order', () => {
    const older = {
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 8,
      fromId: '11111111-1111-7111-8111-111111111111',
      toId: '22222222-2222-7222-8222-222222222222',
      content: 'older',
      createTime: 80
    }
    const newer = {
      id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      seq: 12,
      fromId: '22222222-2222-7222-8222-222222222222',
      toId: '11111111-1111-7111-8111-111111111111',
      content: 'newer',
      createTime: 120
    }
    const duplicateNewer = {
      ...newer,
      content: 'newer-updated'
    }

    expect(mergeConversationMessages([newer], [older, duplicateNewer])).toEqual([
      older,
      duplicateNewer
    ])
  })

  it('finds the latest seq from mapped conversation messages', () => {
    expect(findLatestConversationSeq([
      { id: 'a', seq: 0, createTime: 1 },
      { id: 'b', seq: 4, createTime: 2 },
      { id: 'c', seq: 17, createTime: 3 }
    ])).toBe(17)
  })
})
