import { unwrapResultBody } from './result'

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

export async function executeUploadSession({ http, session, file, operation = 'Upload File' }) {
  const normalized = normalizeUploadSession(session)
  if (!normalized.upload.url) {
    throw new Error('upload.url 缺失，请重新获取上传参数')
  }
  if (normalized.upload.method !== 'POST') {
    throw new Error('暂不支持的上传方法，请重新获取上传参数')
  }

  const form = new FormData()
  Object.entries(normalized.upload.fields).forEach(([key, value]) => {
    form.append(key, value)
  })
  form.append(normalized.upload.fileField || 'file', file)

  const resp = await http.post(normalized.upload.url, form, {
    headers: normalized.upload.headers
  })
  return unwrapResultBody(resp.data, operation)
}
