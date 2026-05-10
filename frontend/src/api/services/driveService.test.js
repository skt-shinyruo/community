import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

vi.mock('../uploadSession', () => ({
  executeUploadSession: vi.fn(),
  normalizeUploadSession: vi.fn((raw = {}) => ({
    uploadId: String(raw.uploadId || ''),
    fileKey: String(raw.fileKey || ''),
    upload: {
      url: String(raw.upload?.url || ''),
      method: String(raw.upload?.method || 'POST').toUpperCase(),
      fileField: String(raw.upload?.fileField || 'file'),
      fields: { ...(raw.upload?.fields || {}) },
      headers: { ...(raw.upload?.headers || {}) }
    },
    constraints: {
      maxBytes: Number(raw.constraints?.maxBytes || 0),
      mimeTypes: Array.isArray(raw.constraints?.mimeTypes) ? raw.constraints.mimeTypes.map(String) : []
    },
    expiresAt: String(raw.expiresAt || '')
  }))
}))

import http from '../http'
import { executeUploadSession } from '../uploadSession'
import {
  createDriveFolder,
  createDriveUploadSession,
  getDriveSpace,
  getPublicDriveShare,
  listDriveEntries,
  listDriveTrash,
  searchDriveEntries,
  uploadDriveFile,
  verifyDriveShare
} from './driveService'

describe('driveService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listDriveEntries should normalize missing data to an array', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: null, traceId: 'trace-1' } })

    const result = await listDriveEntries({ parentId: 'folder-1' })

    expect(http.get).toHaveBeenCalledWith('/api/drive/entries', { params: { parentId: 'folder-1' } })
    expect(result).toEqual({ data: [], traceId: 'trace-1' })
  })

  it('listDriveTrash should normalize missing data to an array', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: null, traceId: 'trace-trash' } })

    const result = await listDriveTrash()

    expect(http.get).toHaveBeenCalledWith('/api/drive/trash')
    expect(result).toEqual({ data: [], traceId: 'trace-trash' })
  })

  it('getDriveSpace should normalize missing data to an empty object', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: null, traceId: 'trace-space' } })

    const result = await getDriveSpace()

    expect(http.get).toHaveBeenCalledWith('/api/drive/space')
    expect(result).toEqual({ data: {}, traceId: 'trace-space' })
  })

  it('createDriveFolder should post parent and folder name', async () => {
    http.post.mockResolvedValue({ data: { code: 0, data: { entryId: 'folder-2', name: 'Docs' }, traceId: 'trace-folder' } })

    const result = await createDriveFolder({ parentId: 'root', name: 'Docs' })

    expect(http.post).toHaveBeenCalledWith('/api/drive/folders', { parentId: 'root', name: 'Docs' })
    expect(result.data.entryId).toBe('folder-2')
  })

  it('createDriveUploadSession should send file metadata and normalize upload instruction', async () => {
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })
    http.post.mockResolvedValue({
      data: {
        code: 0,
        traceId: 'trace-2',
        data: {
          uploadId: 'upload-1',
          upload: { url: '/api/drive/uploads/upload-1/complete', method: 'POST', fileField: 'file', fields: {}, headers: {} },
          constraints: { maxBytes: 1024, mimeTypes: [] },
          expiresAt: '2026-05-09T00:15:00Z'
        }
      }
    })

    const result = await createDriveUploadSession({ parentId: '', file })

    expect(http.post).toHaveBeenCalledWith('/api/drive/uploads', {
      parentId: '',
      fileName: 'hello.txt',
      contentType: 'text/plain',
      contentLength: 5,
      checksumSha256: ''
    })
    expect(result.data.uploadId).toBe('upload-1')
    expect(result.data.upload.url).toBe('/api/drive/uploads/upload-1/complete')
  })

  it('uploadDriveFile should delegate multipart execution to generic upload helper', async () => {
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })
    executeUploadSession.mockResolvedValue({ data: { entryId: 'entry-1' }, traceId: 'trace-upload' })

    const result = await uploadDriveFile({ session: { upload: { url: '/u', method: 'POST' } }, file })

    expect(executeUploadSession).toHaveBeenCalledWith({ http, session: { upload: { url: '/u', method: 'POST' } }, file, operation: '上传网盘文件' })
    expect(result.data.entryId).toBe('entry-1')
  })

  it('searchDriveEntries should call the search endpoint with the query string', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: [{ entryId: '1' }], traceId: 'trace-search' } })

    const result = await searchDriveEntries({ keyword: 'report' })

    expect(http.get).toHaveBeenCalledWith('/api/drive/search', { params: { q: 'report' } })
    expect(result.data).toHaveLength(1)
  })

  it('getPublicDriveShare should load share metadata without a password', async () => {
    http.get.mockResolvedValue({ data: { code: 0, data: { shareToken: 'token-a', name: 'a.txt' }, traceId: 'trace-share-meta' } })

    const result = await getPublicDriveShare('token-a')

    expect(http.get).toHaveBeenCalledWith('/api/drive/shares/token-a')
    expect(result.data.name).toBe('a.txt')
  })

  it('verifyDriveShare should post extraction code to public endpoint', async () => {
    http.post.mockResolvedValue({ data: { code: 0, data: { ticket: 'ticket-a' }, traceId: 'trace-share' } })

    const result = await verifyDriveShare('token-a', '1234')

    expect(http.post).toHaveBeenCalledWith('/api/drive/shares/token-a/verify', { password: '1234' })
    expect(result.data.ticket).toBe('ticket-a')
  })
})
