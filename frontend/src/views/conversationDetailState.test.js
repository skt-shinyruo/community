import { describe, expect, it } from 'vitest'

import {
  buildCanonicalConversationId,
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

  it('builds a stable canonical UUID conversation id independent of input order', () => {
    const lower = '11111111-1111-7111-8111-111111111111'
    const higher = '33333333-3333-7333-8333-333333333333'

    expect(buildCanonicalConversationId(higher, lower)).toBe(`${lower}_${higher}`)
    expect(buildCanonicalConversationId(lower, higher)).toBe(`${lower}_${higher}`)
  })

  it('orders canonical UUID conversation ids with Java signed UUID compare semantics', () => {
    const positiveMostSigBits = '00000000-0000-0000-0000-000000000000'
    const negativeMostSigBits = '80000000-0000-0000-0000-000000000000'
    const positiveLeastSigBits = '00000000-0000-0000-0000-000000000000'
    const negativeLeastSigBits = '00000000-0000-0000-8000-000000000000'

    expect(buildCanonicalConversationId(positiveMostSigBits, negativeMostSigBits))
      .toBe(`${negativeMostSigBits}_${positiveMostSigBits}`)
    expect(buildCanonicalConversationId(positiveLeastSigBits, negativeLeastSigBits))
      .toBe(`${negativeLeastSigBits}_${positiveLeastSigBits}`)
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
