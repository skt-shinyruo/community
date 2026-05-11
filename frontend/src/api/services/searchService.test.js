import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { reindex, searchPosts } from './searchService'

const retiredReindexRoute = ['/api/search/internal', '/reindex'].join('')

describe('api/services/searchService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('searchPosts should preserve UUID category filters in query params', async () => {
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onGet('/api/search/posts').reply((config) => {
      expect(config.params).toMatchObject({
        keyword: '',
        page: 0,
        size: 10,
        categoryId
      })
      return [200, {
        code: 0,
        message: '',
        data: [],
        traceId: 'trace-search-posts'
      }]
    })

    const resp = await searchPosts({ categoryId })

    expect(resp.traceId).toBe('trace-search-posts')
    expect(resp.data).toEqual([])
  })

  it('reindex should call the ops route', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/ops/search/reindex').reply(200, {
      code: 0,
      message: '',
      data: {
        jobId: 'job-1',
        indexedCount: 42
      },
      traceId: 'trace-reindex'
    })

    const resp = await reindex()

    expect(resp.traceId).toBe('trace-reindex')
    expect(resp.data).toEqual({
      jobId: 'job-1',
      indexedCount: 42
    })
  })

  it('reindex should not fall back to the retired internal route', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/ops/search/reindex').reply(404, {
      code: 404,
      message: 'Not Found'
    })
    mock.onPost(retiredReindexRoute).reply(200, {
      code: 0,
      message: '',
      data: {
        jobId: 'retired-route-job',
        indexedCount: 1
      },
      traceId: 'trace-retired-route'
    })

    await expect(reindex()).rejects.toMatchObject({
      response: {
        status: 404
      }
    })
    expect(mock.history.post.map((req) => req.url)).toEqual(['/api/ops/search/reindex'])
  })
})
