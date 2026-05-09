import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { inferMediaKind, preparePostMediaUpload, uploadPostMediaFile } from './postMediaService'

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
    const uploadResponse = { assetId, status: 'UPLOADED' }
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
            fileField: 'mediaFile',
            fields: { uploadToken: 'upload-1' },
            headers: { 'X-Upload-Token': 'token-1' }
          },
          constraints: { maxBytes: 10, mimeTypes: ['image/png'] },
          expiresAt: '2026-05-09T00:00:00Z'
        },
        traceId: 'trace-session'
      }]
    })
    mock.onPost(`/api/posts/media/${assetId}/upload`).reply((config) => {
      expect(config.data).toBeInstanceOf(FormData)
      expect(config.data.get('uploadToken')).toBe('upload-1')
      expect(config.data.get('mediaFile')).toBe(file)
      expect(config.headers).toMatchObject({ 'X-Upload-Token': 'token-1' })
      return [200, { code: 0, message: '', data: uploadResponse, traceId: 'trace-upload' }]
    })

    const file = new File(['demo'], 'demo.png', { type: 'image/png' })
    const session = await preparePostMediaUpload({ file, mediaKind: 'IMAGE' })
    const uploaded = await uploadPostMediaFile({ session: session.data, file })

    expect(session).toEqual({
      data: {
        assetId,
        uploadId: 'upload-1',
        upload: {
          url: `/api/posts/media/${assetId}/upload`,
          method: 'POST',
          fileField: 'mediaFile',
          fields: { uploadToken: 'upload-1' },
          headers: { 'X-Upload-Token': 'token-1' }
        },
        constraints: { maxBytes: 10, mimeTypes: ['image/png'] },
        expiresAt: '2026-05-09T00:00:00Z'
      },
      traceId: 'trace-session'
    })
    expect(uploaded).toEqual({ data: uploadResponse, traceId: 'trace-upload' })
  })

  it('infers media kind from file content type', () => {
    expect(inferMediaKind(new File(['image'], 'demo.png', { type: 'image/png' }))).toBe('IMAGE')
    expect(inferMediaKind(new File(['video'], 'demo.mp4', { type: 'video/mp4' }))).toBe('VIDEO')
    expect(inferMediaKind(new File(['data'], 'demo.pdf', { type: 'application/pdf' }))).toBe('FILE')
    expect(inferMediaKind({})).toBe('FILE')
  })
})
