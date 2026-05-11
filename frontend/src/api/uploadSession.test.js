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
    const http = { post: vi.fn().mockResolvedValue({ data: { code: 0, data: {}, traceId: 'trace-upload' } }) }
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

    const result = await executeUploadSession({ http, session, file })

    expect(result.traceId).toBe('trace-upload')
    expect(http.post).toHaveBeenCalledWith('/api/oss/objects/object-1/complete', expect.any(FormData), {
      headers: { 'X-Test': '1' }
    })
    const form = http.post.mock.calls[0][1]
    expect(form.get('sessionId')).toBe('session-1')
    expect(form.get('versionId')).toBe('version-1')
    expect(form.get('file')).toBe(file)
  })

  it('rejects unsupported methods before sending a request', async () => {
    const http = { post: vi.fn() }
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    const session = normalizeUploadSession({
      upload: {
        url: '/api/oss/objects/object-1/complete',
        method: 'PUT',
        fileField: 'file',
        fields: {}
      }
    })

    await expect(executeUploadSession({ http, session, file })).rejects.toThrow('暂不支持的上传方法')
    expect(http.post).not.toHaveBeenCalled()
  })
})
