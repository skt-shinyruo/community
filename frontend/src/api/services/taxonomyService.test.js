import { afterEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { listCategories, listHotTags, suggestTags } from './taxonomyService'

describe('api/services/taxonomyService', () => {
  let mock

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('should keep category payload unchanged when API already returns healthy text', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onGet('/api/categories').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          id: 1,
          name: '公告',
          description: '官方公告/规则',
          position: 0,
          postCount: 0
        }
      ],
      traceId: null,
      timestamp: 1774060182920
    })

    const resp = await listCategories()

    expect(resp.data).toEqual([
      {
        id: 1,
        name: '公告',
        description: '官方公告/规则',
        position: 0,
        postCount: 0
      }
    ])
  })

  it('should preserve hot-tag objects instead of coercing them to strings', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onGet('/api/tags/hot').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          name: 'Java',
          useCount: 12
        },
        {
          name: 'Spring',
          useCount: 7
        }
      ],
      traceId: null,
      timestamp: 1774060182920
    })

    const resp = await listHotTags()

    expect(resp.data).toEqual([
      {
        name: 'Java',
        useCount: 12
      },
      {
        name: 'Spring',
        useCount: 7
      }
    ])
  })

  it('should preserve suggested-tag objects instead of coercing them to strings', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onGet('/api/tags/suggest').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          name: 'Vue',
          useCount: 5
        }
      ],
      traceId: null,
      timestamp: 1774060182920
    })

    const resp = await suggestTags({ q: 'vu', limit: 8 })

    expect(resp.data).toEqual([
      {
        name: 'Vue',
        useCount: 5
      }
    ])
  })
})
