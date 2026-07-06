import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { addComment, createPost, listBoardFeed, listGlobalFeed, updatePost } from './postService'

describe('api/services/postService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('listGlobalFeed should call /api/feed/global with cursor params', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/feed/global').reply((config) => {
      expect(config.params).toEqual({ cursor: 'cursor-1', size: 12 })
      return [200, { code: 0, message: '', data: { items: [], nextCursor: 'cursor-2', rankVersion: 'rank-v1' }, traceId: 'trace-feed' }]
    })

    const resp = await listGlobalFeed({ cursor: 'cursor-1', size: 12 })

    expect(resp.traceId).toBe('trace-feed')
    expect(resp.data.nextCursor).toBe('cursor-2')
    expect(resp.data.rankVersion).toBe('rank-v1')
  })

  it('listBoardFeed should call /api/boards/{boardId}/feed with cursor params', async () => {
    const boardId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onGet(`/api/boards/${boardId}/feed`).reply((config) => {
      expect(config.params).toEqual({ size: 20 })
      return [200, { code: 0, message: '', data: { items: [], nextCursor: '', rankVersion: 'rank-board-v1' }, traceId: 'trace-board-feed' }]
    })

    const resp = await listBoardFeed(boardId)

    expect(resp.traceId).toBe('trace-board-feed')
    expect(resp.data.items).toEqual([])
  })

  it('addComment should send parentCommentId and replyToUserId', async () => {
    const postId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    const parentCommentId = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
    const replyToUserId = 'dddddddd-dddd-7ddd-8ddd-dddddddddddd'
    mock = new MockAdapter(http)
    mock.onPost(`/api/posts/${postId}/comments`).reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        content: '回复内容',
        parentCommentId,
        replyToUserId
      })
      return [200, { code: 0, message: '', data: { id: 'reply-1' }, traceId: 'trace-add-comment' }]
    })

    const resp = await addComment(postId, {
      content: '回复内容',
      parentCommentId,
      replyToUserId
    })

    expect(resp.traceId).toBe('trace-add-comment')
    expect(resp.data).toEqual({ id: 'reply-1' })
  })

  it('createPost and updatePost should normalize block payloads without content shortcuts', async () => {
    const postId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const assetId = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
    const imageAssetId = 'dddddddd-dddd-7ddd-8ddd-dddddddddddd'
    const videoAssetId = 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee'
    const fileAssetId = 'ffffffff-ffff-7fff-8fff-ffffffffffff'
    const metadata = { width: 800, height: 600 }
    const blocks = [
      { type: ' paragraph ', text: 'world', caption: undefined },
      { type: 'code', text: 123, language: 'js' },
      { type: 'image', assetId, caption: 'chart', displayName: 'chart.png', metadata },
      { type: 'video', assetId: imageAssetId, caption: 'demo', displayName: 'demo.mp4', metadata: { durationSeconds: 9 } },
      { type: 'file', assetId: videoAssetId, text: 'download', caption: 'archive', displayName: 'report.zip', metadata: { byteSize: 1000 } },
      { type: 'file', assetId: fileAssetId, text: undefined, caption: undefined, displayName: undefined, metadata: undefined }
    ]
    const normalizedBlocks = [
      { type: 'paragraph', text: 'world' },
      { type: 'code', text: '123', language: 'js' },
      { type: 'image', assetId, caption: 'chart', displayName: 'chart.png', metadata },
      { type: 'video', assetId: imageAssetId, caption: 'demo', displayName: 'demo.mp4', metadata: { durationSeconds: 9 } },
      { type: 'file', assetId: videoAssetId, text: 'download', caption: 'archive', displayName: 'report.zip', metadata: { byteSize: 1000 } },
      { type: 'file', assetId: fileAssetId }
    ]
    mock = new MockAdapter(http)
    const seen = []
    mock.onPost('/api/posts').reply((config) => {
      seen.push(JSON.parse(config.data))
      return [200, { code: 0, message: '', data: { postId }, traceId: 'trace-create-post' }]
    })
    mock.onPut(`/api/posts/${postId}`).reply((config) => {
      seen.push(JSON.parse(config.data))
      return [200, { code: 0, message: '', data: null, traceId: 'trace-update-post' }]
    })

    await createPost({ title: 'hello', blocks, categoryId })
    await updatePost(postId, { title: 'hello', blocks, categoryId })

    expect(seen).toEqual([
      { title: 'hello', blocks: normalizedBlocks, categoryId },
      { title: 'hello', blocks: normalizedBlocks, categoryId }
    ])
    expect(seen[0]).not.toHaveProperty('content')
    expect(seen[1]).not.toHaveProperty('content')
    expect(seen[0].blocks[0]).not.toHaveProperty('caption')
    expect(seen[0].blocks[5]).not.toHaveProperty('text')
    expect(seen[0].blocks[5]).not.toHaveProperty('caption')
    expect(seen[0].blocks[5]).not.toHaveProperty('displayName')
    expect(seen[0].blocks[5]).not.toHaveProperty('metadata')
  })
})
