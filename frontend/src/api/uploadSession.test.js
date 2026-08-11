import { describe, expect, it, vi } from 'vitest'
import { executeUploadSession, normalizeUploadSession } from './uploadSession'

describe('uploadSession', () => {
  it('normalizes a backend upload session without provider fields', () => {
    const session = normalizeUploadSession({
      uploadId: 'session-1',
      objectId: 'object-1',
      versionId: 'version-1',
      upload: {
        url: '/api/oss/objects/object-1/complete',
        method: 'POST',
        fileField: 'file',
        fields: { sessionId: 'session-1', versionId: 'version-1' },
        headers: {}
      },
      constraints: {
        maxBytes: 1024,
        mimeTypes: ['image/png']
      },
      expiresAt: '2026-05-08T12:00:00Z'
    })

    expect(session.uploadId).toBe('session-1')
    expect(session.objectId).toBe('object-1')
    expect(session.versionId).toBe('version-1')
    expect(session.upload.url).toBe('/api/oss/objects/object-1/complete')
    expect(session.upload.method).toBe('POST')
    expect(session.upload.fileField).toBe('file')
    expect(session.upload.fields).toEqual({ sessionId: 'session-1', versionId: 'version-1' })
    expect(session.constraints.mimeTypes).toEqual(['image/png'])
  })

  it('posts multipart form data using generic upload instructions', async () => {
    const transport = { upload: vi.fn().mockResolvedValue({ data: { code: 0, data: {}, traceId: 'trace-upload' } }) }
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const session = normalizeUploadSession({
      upload: {
        url: '/api/oss/objects/object-1/complete',
        method: 'POST',
        fileField: 'file',
        fields: { sessionId: 'session-1', versionId: 'version-1' },
        headers: { 'X-Test': '1' }
      }
    })

    const result = await executeUploadSession({ transport, session, file })

    expect(result.traceId).toBe('trace-upload')
    expect(transport.upload).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/oss/objects/object-1/complete',
      method: 'POST',
      data: expect.any(FormData),
      headers: { 'X-Test': '1' }
    }))
    const form = transport.upload.mock.calls[0][0].data
    expect(form.get('sessionId')).toBe('session-1')
    expect(form.get('versionId')).toBe('version-1')
    expect(form.get('file')).toBe(file)
  })

  it('puts the raw file with provider-signed headers', async () => {
    const transport = { upload: vi.fn().mockResolvedValue({ data: { etag: 'etag-1' } }) }
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const session = normalizeUploadSession({
      upload: {
        url: 'https://objects.example.test/bucket/key?signature=1',
        method: 'PUT',
        headers: {
          'Content-Type': 'image/png',
          Authorization: 'AWS4-HMAC-SHA256 signed-value'
        }
      }
    })

    await expect(executeUploadSession({ transport, session, file })).resolves.toEqual({
      data: { etag: 'etag-1' },
      traceId: ''
    })
    expect(transport.upload).toHaveBeenCalledWith(expect.objectContaining({
      url: 'https://objects.example.test/bucket/key?signature=1',
      method: 'PUT',
      data: file,
      headers: {
        'Content-Type': 'image/png',
        Authorization: 'AWS4-HMAC-SHA256 signed-value'
      }
    }))
  })

  it('rejects non-upload methods before sending a request', async () => {
    const transport = { upload: vi.fn() }
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const session = normalizeUploadSession({
      upload: { url: '/api/oss/objects/object-1', method: 'DELETE' }
    })

    await expect(executeUploadSession({ transport, session, file })).rejects.toThrow('暂不支持的上传方法')
    expect(transport.upload).not.toHaveBeenCalled()
  })

  it('rejects oversized and disallowed MIME files before building the request', async () => {
    const transport = { upload: vi.fn() }
    const oversized = new File(['12345'], 'large.txt', { type: 'text/plain' })
    const wrongType = new File(['1'], 'avatar.jpg', { type: 'image/jpeg' })
    const session = normalizeUploadSession({
      upload: { url: '/api/upload', method: 'POST' },
      constraints: { maxBytes: 4, mimeTypes: ['image/png'] }
    })

    await expect(executeUploadSession({ transport, session, file: oversized })).rejects.toThrow('文件大小超过上传限制')
    await expect(executeUploadSession({ transport, session, file: wrongType })).rejects.toThrow('文件类型不符合上传限制')
    expect(transport.upload).not.toHaveBeenCalled()
  })

  it('accepts a provider response for protocol-relative external upload URLs', async () => {
    const transport = { upload: vi.fn().mockResolvedValue({ data: { etag: 'etag-1' } }) }
    const session = normalizeUploadSession({
      upload: { url: '//objects.example.test/upload', method: 'POST' }
    })

    await expect(executeUploadSession({
      transport,
      session,
      file: new File(['file'], 'file.txt', { type: 'text/plain' })
    })).resolves.toEqual({ data: { etag: 'etag-1' }, traceId: '' })
  })
})
