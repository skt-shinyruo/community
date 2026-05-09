import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { createPost, listPosts, updatePost } from './postService'

describe('api/services/postService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('listPosts should preserve UUID category filters in query params', async () => {
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onGet('/api/posts').reply((config) => {
      expect(config.params).toMatchObject({
        order: 'latest',
        page: 0,
        size: 10,
        categoryId
      })
      return [200, {
        code: 0,
        message: '',
        data: [],
        traceId: 'trace-list-posts'
      }]
    })

    const resp = await listPosts({ categoryId })

    expect(resp.traceId).toBe('trace-list-posts')
    expect(resp.data).toEqual([])
  })

  it('createPost and updatePost should normalize block payloads without legacy content', async () => {
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
