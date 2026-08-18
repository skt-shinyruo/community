import { describe, expect, it } from 'vitest'

import {
  buildCanonicalConversationId,
  advanceConversationSeqWaterline,
  commitPendingConversationMessage,
  createPendingConversationMessage,
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
    })).toMatchObject({
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

    expect(mergeConversationMessages([newer], [older, duplicateNewer])).toMatchObject([
      older,
      duplicateNewer
    ])
  })

  it('merges messages when any sequence, server, client, or request id matches', () => {
    const base = {
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 8,
      fromId: '11111111-1111-7111-8111-111111111111',
      clientMsgId: 'client-a',
      requestId: 'request-a',
      createTime: 80,
      content: 'base'
    }
    const bySeq = { ...base, id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', clientMsgId: 'client-b', requestId: 'request-b', content: 'seq replacement' }
    const byId = { ...base, seq: 9, id: ' aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa ', clientMsgId: 'client-c', requestId: 'request-c', content: 'id replacement' }
    const byClientMsgId = { ...base, seq: 10, id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc', clientMsgId: ' client-a ', requestId: 'request-d', content: 'client replacement' }
    const byRequestId = { ...base, seq: 11, id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd', clientMsgId: 'client-e', requestId: ' request-a ', content: 'request replacement' }

    expect(mergeConversationMessages([base], [bySeq])).toMatchObject([bySeq])
    expect(mergeConversationMessages([base], [byId])).toMatchObject([byId])
    expect(mergeConversationMessages([base], [byClientMsgId])).toMatchObject([byClientMsgId])
    expect(mergeConversationMessages([base], [byRequestId])).toMatchObject([{
      ...byRequestId,
      messageIdentity: { requestIds: ['request-a'] }
    }])
  })

  it('transfers all aliases when one incoming message bridges two active records', () => {
    const fromId = '11111111-1111-7111-8111-111111111111'
    const first = { id: 'first-id', seq: 1, fromId, clientMsgId: 'first-client', createTime: 100, content: 'first' }
    const second = { id: 'second-id', seq: 2, fromId, clientMsgId: 'second-client', createTime: 200, content: 'second' }
    const bridge = { id: ' second-id ', seq: 1, fromId, clientMsgId: 'bridge-client', createTime: 150, content: 'bridge' }
    const followUp = { id: 'follow-up-id', seq: 3, fromId, clientMsgId: 'second-client', createTime: 300, content: 'follow-up' }

    expect(mergeConversationMessages([first, second], [bridge, followUp])).toMatchObject([followUp])
  })

  it('retains replaced identities across separate merge calls', () => {
    const fromId = '11111111-1111-7111-8111-111111111111'
    const current = { id: 'message-a', seq: 8, fromId, clientMsgId: 'client-a', createTime: 100, content: 'current' }
    const replacementById = { id: ' message-a ', seq: 7, fromId, clientMsgId: 'client-b', createTime: 200, content: 'by id' }
    const replacementByOldClient = { id: 'message-c', seq: 6, fromId, clientMsgId: 'client-a', createTime: 300, content: 'by old client' }

    const firstMerge = mergeConversationMessages([current], [replacementById])

    expect(mergeConversationMessages(firstMerge, [replacementByOldClient])).toMatchObject([replacementByOldClient])
  })

  it('scopes client message identity by sender', () => {
    const sharedClientMsgId = 'shared-client-id'
    const localPending = {
      id: 'pending:shared-client-id',
      seq: 0,
      fromId: '11111111-1111-7111-8111-111111111111',
      clientMsgId: sharedClientMsgId,
      createTime: 100,
      content: 'local pending'
    }
    const peerMessage = {
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 9,
      fromId: '22222222-2222-7222-8222-222222222222',
      clientMsgId: sharedClientMsgId,
      createTime: 200,
      content: 'peer committed'
    }
    const localCommitted = {
      id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      seq: 10,
      fromId: localPending.fromId,
      clientMsgId: sharedClientMsgId,
      createTime: 300,
      content: 'local committed'
    }

    const withPeer = mergeConversationMessages([localPending], [peerMessage])
    expect(withPeer).toMatchObject([localPending, peerMessage])
    expect(mergeConversationMessages(withPeer, [localCommitted])).toMatchObject([peerMessage, localCommitted])
  })

  it('does not treat whitespace-only client message ids as an identity', () => {
    const first = { id: 'first-id', seq: 1, clientMsgId: '   ', createTime: 100, content: 'first' }
    const second = { id: 'second-id', seq: 2, clientMsgId: '\t', createTime: 200, content: 'second' }

    expect(mergeConversationMessages([first], [second])).toMatchObject([first, second])
  })

  it('keeps merged messages in chronological order after identity replacement', () => {
    const first = { id: 'a', seq: 1, clientMsgId: 'one', createTime: 100, content: 'first' }
    const second = { id: 'b', seq: 2, clientMsgId: 'two', createTime: 200, content: 'second' }
    const replacement = { id: ' b ', seq: 2, clientMsgId: 'two-updated', createTime: 200, content: 'second updated' }

    expect(mergeConversationMessages([second], [first, replacement])).toMatchObject([first, replacement])
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

  it('replaces a pending send with its committed identity through clientMsgId', () => {
    const pending = createPendingConversationMessage({
      clientMsgId: 'client-pending',
      fromId: '11111111-1111-7111-8111-111111111111',
      toId: '22222222-2222-7222-8222-222222222222',
      content: 'pending message',
      createTime: 100
    })
    expect(pending.messageIdentity).toMatchObject({
      clientMessageIds: [{ fromId: pending.fromId, clientMsgId: 'client-pending' }]
    })
    const committed = commitPendingConversationMessage(pending, {
      clientMsgId: 'client-pending',
      requestId: 'request-pending',
      messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 9
    })

    expect(mergeConversationMessages([pending], [committed])).toMatchObject([{
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      seq: 9,
      deliveryState: 'committed',
      messageIdentity: {
        serverMessageIds: ['aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'],
        clientMessageIds: [{ fromId: pending.fromId, clientMsgId: 'client-pending' }],
        requestIds: ['request-pending'],
        sequences: [9]
      }
    }])
  })

  it('models multiple identifiers for one committed message fact', () => {
    const pending = createPendingConversationMessage({
      clientMsgId: 'client-alias',
      fromId: '11111111-1111-7111-8111-111111111111',
      toId: '22222222-2222-7222-8222-222222222222',
      content: 'one fact',
      createTime: 100
    })

    expect(commitPendingConversationMessage(pending, {
      clientMsgId: 'client-alias',
      requestId: 'request-alias',
      messageId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      seq: 10
    }).messageIdentity).toEqual({
      serverMessageIds: ['bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'],
      clientMessageIds: [{ fromId: pending.fromId, clientMsgId: 'client-alias' }],
      requestIds: ['request-alias'],
      sequences: [10]
    })
  })

  it('does not downgrade a committed message when its optimistic copy arrives out of order', () => {
    const pending = createPendingConversationMessage({
      clientMsgId: 'client-out-of-order',
      fromId: '11111111-1111-7111-8111-111111111111',
      toId: '22222222-2222-7222-8222-222222222222',
      content: 'already committed',
      createTime: 100
    })
    const committed = commitPendingConversationMessage(pending, {
      clientMsgId: 'client-out-of-order',
      requestId: 'request-out-of-order',
      messageId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      seq: 11
    })

    expect(mergeConversationMessages([committed], [pending])).toMatchObject([{
      id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      seq: 11,
      deliveryState: 'committed',
      messageIdentity: {
        requestIds: ['request-out-of-order']
      }
    }])
  })

  it('deduplicates repeated backfill while retaining committed aliases', () => {
    const fromId = '11111111-1111-7111-8111-111111111111'
    const toId = '22222222-2222-7222-8222-222222222222'
    const pending = createPendingConversationMessage({
      clientMsgId: 'client-backfill',
      fromId,
      toId,
      content: 'optimistic',
      createTime: 100
    })
    const committed = commitPendingConversationMessage(pending, {
      clientMsgId: 'client-backfill',
      requestId: 'request-backfill',
      messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      seq: 12
    })
    const backfill = mapConversationMessage({
      seq: 12,
      messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      fromUserId: fromId,
      toUserId: toId,
      content: 'persisted backfill',
      clientMsgId: 'client-backfill',
      createdAtEpochMs: 101
    })

    expect(backfill.messageIdentity).toEqual({
      serverMessageIds: ['dddddddd-dddd-7ddd-8ddd-dddddddddddd'],
      clientMessageIds: [{ fromId, clientMsgId: 'client-backfill' }],
      requestIds: [],
      sequences: [12]
    })
    expect(mergeConversationMessages([committed], [backfill, backfill])).toMatchObject([{
      content: 'persisted backfill',
      messageIdentity: { requestIds: ['request-backfill'] }
    }])
  })

  it('only advances an HTTP waterline through contiguous message sequences', () => {
    expect(advanceConversationSeqWaterline(8, [
      { seq: 11 },
      { seq: 9 },
      { seq: 9 },
      { seq: 0 }
    ])).toBe(9)
    expect(advanceConversationSeqWaterline(9, [
      { seq: 11 },
      { seq: 10 }
    ])).toBe(11)
  })
})
