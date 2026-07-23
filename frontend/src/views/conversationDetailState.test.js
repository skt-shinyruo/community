import { describe, expect, it } from 'vitest'

import {
  buildCanonicalConversationId,
  findLatestConversationSeq,
  mapConversationMessage,
  mergeConversations,
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
      clientMsgId: 'client-12',
      createdAtEpochMs: 123456789
    })).toEqual({
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 12,
      fromId: '11111111-1111-7111-8111-111111111111',
      toId: '22222222-2222-7222-8222-222222222222',
      content: 'hello',
      clientMsgId: 'client-12',
      createTime: 123456789
    })
  })

  it('rejects conversation messages that violate the API identity contract', () => {
    const valid = {
      seq: 12,
      messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      fromUserId: '11111111-1111-7111-8111-111111111111',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: 'hello',
      createdAtEpochMs: 123456789
    }

    expect(() => mapConversationMessage({ ...valid, seq: 0 })).toThrow('seq 非法')
    expect(() => mapConversationMessage({ ...valid, messageId: null })).toThrow('messageId 缺失')
    expect(() => mapConversationMessage({ ...valid, fromUserId: 'bad' })).toThrow('fromUserId 非法')
    expect(() => mapConversationMessage({ ...valid, createdAtEpochMs: 0 })).toThrow('createdAtEpochMs 非法')
  })

  it('rejects merging already-mapped messages without server identity', () => {
    expect(() => mergeConversationMessages([], [{
      id: '',
      seq: 0,
      fromId: '11111111-1111-7111-8111-111111111111',
      toId: '22222222-2222-7222-8222-222222222222',
      content: 'fallback should not be used',
      createTime: 123456789
    }])).toThrow('message identity 缺失')
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

  it('merges messages when any positive seq, normalized id, or client message id matches', () => {
    const base = {
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 8,
      clientMsgId: 'client-a',
      createTime: 80,
      content: 'base'
    }
    const bySeq = { ...base, id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', clientMsgId: 'client-b', content: 'seq replacement' }
    const byId = { ...base, seq: 9, id: ' aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa ', clientMsgId: 'client-c', content: 'id replacement' }
    const byClientMsgId = { ...base, seq: 10, id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc', clientMsgId: ' client-a ', content: 'client replacement' }

    expect(mergeConversationMessages([base], [bySeq])).toEqual([bySeq])
    expect(mergeConversationMessages([base], [byId])).toEqual([byId])
    expect(mergeConversationMessages([base], [byClientMsgId])).toEqual([byClientMsgId])
  })

  it('transfers all aliases when one incoming message bridges two active records', () => {
    const first = { id: 'first-id', seq: 1, clientMsgId: 'first-client', createTime: 100, content: 'first' }
    const second = { id: 'second-id', seq: 2, clientMsgId: 'second-client', createTime: 200, content: 'second' }
    const bridge = { id: ' second-id ', seq: 1, clientMsgId: 'bridge-client', createTime: 150, content: 'bridge' }
    const followUp = { id: 'follow-up-id', seq: 3, clientMsgId: 'second-client', createTime: 300, content: 'follow-up' }

    expect(mergeConversationMessages([first, second], [bridge, followUp])).toEqual([followUp])
  })

  it('retains replaced identities across separate merge calls', () => {
    const current = { id: 'message-a', seq: 8, clientMsgId: 'client-a', createTime: 100, content: 'current' }
    const replacementById = { id: ' message-a ', seq: 7, clientMsgId: 'client-b', createTime: 200, content: 'by id' }
    const replacementByOldClient = { id: 'message-c', seq: 6, clientMsgId: 'client-a', createTime: 300, content: 'by old client' }

    const firstMerge = mergeConversationMessages([current], [replacementById])

    expect(mergeConversationMessages(firstMerge, [replacementByOldClient])).toEqual([replacementByOldClient])
  })

  it('does not treat whitespace-only client message ids as an identity', () => {
    const first = { id: 'first-id', seq: 1, clientMsgId: '   ', createTime: 100, content: 'first' }
    const second = { id: 'second-id', seq: 2, clientMsgId: '\t', createTime: 200, content: 'second' }

    expect(mergeConversationMessages([first], [second])).toEqual([first, second])
  })

  it('keeps merged messages in chronological order after identity replacement', () => {
    const first = { id: 'a', seq: 1, clientMsgId: 'one', createTime: 100, content: 'first' }
    const second = { id: 'b', seq: 2, clientMsgId: 'two', createTime: 200, content: 'second' }
    const replacement = { id: ' b ', seq: 2, clientMsgId: 'two-updated', createTime: 200, content: 'second updated' }

    expect(mergeConversationMessages([second], [first, replacement])).toEqual([first, replacement])
  })

  it('replaces duplicate conversations by normalized id while preserving stable order', () => {
    const first = { conversationId: ' c1 ', unreadCount: 1 }
    const second = { conversationId: 'c2', unreadCount: 2 }
    const replacement = { conversationId: 'c1', unreadCount: 9 }

    expect(mergeConversations([first, second], [replacement])).toEqual([replacement, second])
  })

  it('finds the latest seq from mapped conversation messages', () => {
    expect(findLatestConversationSeq([
      { id: 'a', seq: 0, createTime: 1 },
      { id: 'b', seq: 4, createTime: 2 },
      { id: 'c', seq: 17, createTime: 3 }
    ])).toBe(17)
  })
})
