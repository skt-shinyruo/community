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

  it('createPost and updatePost should preserve UUID category ids in payloads', async () => {
    const postId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    const seen = []
    mock.onPost('/api/posts').reply((config) => {
      seen.push(JSON.parse(config.data))
      return [200, {
        code: 0,
        message: '',
        data: { postId },
        traceId: 'trace-create-post'
      }]
    })
    mock.onPut(`/api/posts/${postId}`).reply((config) => {
      seen.push(JSON.parse(config.data))
      return [200, {
        code: 0,
        message: '',
        data: null,
        traceId: 'trace-update-post'
      }]
    })

    const created = await createPost({
      title: 'hello',
      content: 'world',
      categoryId
    })
    const updated = await updatePost(postId, {
      title: 'hello',
      content: 'world',
      categoryId
    })

    expect(created.traceId).toBe('trace-create-post')
    expect(updated.traceId).toBe('trace-update-post')
    expect(seen).toEqual([
      { title: 'hello', content: 'world', categoryId },
      { title: 'hello', content: 'world', categoryId }
    ])
  })
})
