import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { preparePostMediaUpload, uploadPostMediaFile } from './postMediaService'

describe('api/services/postMediaService', () => {
  let mock

  beforeEach(() => setActivePinia(createPinia()))
  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('prepares and executes post media upload sessions', async () => {
    mock = new MockAdapter(http)
    const assetId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock.onPost('/api/posts/media/upload-sessions').reply((config) => {
      expect(JSON.parse(config.data)).toMatchObject({
        fileName: 'demo.png',
        contentType: 'image/png',
        contentLength: 4,
        mediaKind: 'IMAGE'
      })
      return [200, {
        code: 0,
        message: '',
        data: {
          assetId,
          uploadId: 'upload-1',
          upload: {
            url: `/api/posts/media/${assetId}/upload`,
            method: 'POST',
            fileField: 'file',
            fields: { uploadId: 'upload-1' },
            headers: {}
          },
          constraints: { maxBytes: 10, mimeTypes: ['image/png'] },
          expiresAt: '2026-05-09T00:00:00Z'
        },
        traceId: 'trace-session'
      }]
    })
    mock.onPost(`/api/posts/media/${assetId}/upload`).reply((config) => {
      expect(config.data).toBeInstanceOf(FormData)
      return [200, { code: 0, message: '', data: null, traceId: 'trace-upload' }]
    })

    const file = new File(['demo'], 'demo.png', { type: 'image/png' })
    const session = await preparePostMediaUpload({ file, mediaKind: 'IMAGE' })
    const uploaded = await uploadPostMediaFile({ session: session.data, file })

    expect(session.data.assetId).toBe(assetId)
    expect(uploaded.traceId).toBe('trace-upload')
  })
})
