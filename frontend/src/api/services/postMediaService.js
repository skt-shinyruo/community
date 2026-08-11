import http from '../http'
import { unwrapResultBody } from '../result'
import { executeUploadSession } from '../uploadSession'

export async function preparePostMediaUpload({ file, mediaKind, checksumSha256 = '', signal } = {}) {
  const payload = {
    fileName: String(file?.name || ''),
    contentType: String(file?.type || 'application/octet-stream'),
    contentLength: Number(file?.size || 0),
    mediaKind: String(mediaKind || inferMediaKind(file)).toUpperCase(),
    checksumSha256
  }
  const resp = await http.post('/api/posts/media/upload-sessions', payload, { signal })
  const { data, traceId } = unwrapResultBody(resp.data, '创建帖子媒体上传会话')
  return { data: normalizePostMediaSession(data), traceId }
}

export async function uploadPostMediaFile({ session, file, signal, onProgress } = {}) {
  const { data, traceId } = await executeUploadSession({ session, file, signal, onProgress, operation: '上传帖子媒体' })
  return { data, traceId }
}

export function inferMediaKind(file) {
  const type = String(file?.type || '').toLowerCase()
  if (type.startsWith('image/')) return 'IMAGE'
  if (type.startsWith('video/')) return 'VIDEO'
  return 'FILE'
}

function normalizePostMediaSession(raw = {}) {
  return {
    ...raw,
    assetId: String(raw.assetId || ''),
    uploadId: String(raw.uploadId || '')
  }
}
