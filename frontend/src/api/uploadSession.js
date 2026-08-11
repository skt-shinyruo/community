import { unwrapResultBody } from './result'
import { isExternalUploadUrl, uploadTransport } from './uploadTransport'

export function normalizeUploadSession(raw = {}) {
  const upload = raw.upload || {}
  const constraints = raw.constraints || {}
  return {
    uploadId: String(raw.uploadId || ''),
    objectId: String(raw.objectId || ''),
    versionId: String(raw.versionId || ''),
    fileKey: String(raw.fileKey || ''),
    upload: {
      url: String(upload.url || ''),
      method: String(upload.method || 'POST').toUpperCase(),
      fileField: String(upload.fileField || 'file'),
      fields: { ...(upload.fields || {}) },
      headers: { ...(upload.headers || {}) }
    },
    constraints: {
      maxBytes: Number(constraints.maxBytes || 0),
      mimeTypes: Array.isArray(constraints.mimeTypes) ? constraints.mimeTypes.map(String) : []
    },
    expiresAt: String(raw.expiresAt || '')
  }
}

function mimeTypeAllowed(fileType, acceptedType) {
  const fileMime = String(fileType || '').trim().toLowerCase()
  const accepted = String(acceptedType || '').trim().toLowerCase()
  if (!accepted) return false
  if (accepted === '*/*') return true
  if (accepted.endsWith('/*')) return fileMime.startsWith(accepted.slice(0, -1))
  return fileMime === accepted
}

export function validateUploadFile(session, file) {
  const normalized = normalizeUploadSession(session)
  if (!file || typeof file !== 'object') {
    throw new Error('请选择要上传的文件')
  }

  const maxBytes = normalized.constraints.maxBytes
  const fileSize = Number(file.size || 0)
  if (maxBytes > 0 && fileSize > maxBytes) {
    throw new Error(`文件大小超过上传限制（最大 ${maxBytes} 字节）`)
  }

  const mimeTypes = normalized.constraints.mimeTypes
  if (mimeTypes.length > 0 && !mimeTypes.some((accepted) => mimeTypeAllowed(file.type, accepted))) {
    throw new Error(`文件类型不符合上传限制（允许 ${mimeTypes.join(', ')}）`)
  }
  return normalized
}

export async function executeUploadSession({
  transport = uploadTransport,
  session,
  file,
  operation = 'Upload File',
  signal,
  onProgress
}) {
  const normalized = validateUploadFile(session, file)
  if (!normalized.upload.url) {
    throw new Error('upload.url 缺失，请重新获取上传参数')
  }
  if (!['POST', 'PUT'].includes(normalized.upload.method)) {
    throw new Error('暂不支持的上传方法，请重新获取上传参数')
  }

  let requestBody = file
  if (normalized.upload.method === 'POST') {
    const form = new FormData()
    Object.entries(normalized.upload.fields).forEach(([key, value]) => {
      form.append(key, value)
    })
    form.append(normalized.upload.fileField || 'file', file)
    requestBody = form
  }

  const resp = await transport.upload({
    url: normalized.upload.url,
    method: normalized.upload.method,
    data: requestBody,
    headers: normalized.upload.headers,
    signal,
    onProgress
  })
  if (resp?.data?.code == null && isExternalUploadUrl(normalized.upload.url)) {
    return { data: resp?.data || {}, traceId: '' }
  }
  return unwrapResultBody(resp.data, operation)
}
