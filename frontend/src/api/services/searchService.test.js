import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import * as searchService from './searchService'

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

    const resp = await searchService.searchPosts({ categoryId })

    expect(resp.traceId).toBe('trace-search-posts')
    expect(resp.data).toEqual([])
  })

  it('does not expose the retired reindex operation', () => {
    expect(Object.keys(searchService)).toEqual(['searchPosts'])
  })
})
