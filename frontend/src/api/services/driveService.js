import http from '../http'
import { unwrapResultBody } from '../result'
import { executeUploadSession, normalizeUploadSession } from '../uploadSession'

export async function getDriveSpace() {
  const resp = await http.get('/api/drive/space')
  const { data, traceId } = unwrapResultBody(resp.data, '获取网盘空间')
  return { data: data || {}, traceId }
}

export async function listDriveEntries({ parentId = '' } = {}) {
  const resp = await http.get('/api/drive/entries', { params: { parentId } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询网盘文件')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listDriveTrash() {
  const resp = await http.get('/api/drive/trash')
  const { data, traceId } = unwrapResultBody(resp.data, '查询回收站')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createDriveFolder({ parentId = '', name }) {
  const resp = await http.post('/api/drive/folders', { parentId, name: String(name || '') })
  const { data, traceId } = unwrapResultBody(resp.data, '新建文件夹')
  return { data: data || {}, traceId }
}

export async function searchDriveEntries({ keyword = '' } = {}) {
  const resp = await http.get('/api/drive/search', { params: { q: String(keyword || '') } })
  const { data, traceId } = unwrapResultBody(resp.data, '搜索网盘文件')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createDriveUploadSession({ parentId = '', file, checksumSha256 = '' } = {}) {
  const payload = {
    parentId,
    fileName: String(file?.name || ''),
    contentType: String(file?.type || 'application/octet-stream'),
    contentLength: Number(file?.size || 0),
    checksumSha256
  }
  const resp = await http.post('/api/drive/uploads', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建网盘上传会话')
  return { data: normalizeUploadSession(data || {}), traceId }
}

export async function uploadDriveFile({ session, file } = {}) {
  const { data, traceId } = await executeUploadSession({ http, session, file, operation: '上传网盘文件' })
  return { data: data || {}, traceId }
}

export async function renameDriveEntry(entryId, payload) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/rename`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '重命名网盘条目')
  return { data: data || {}, traceId }
}

export async function moveDriveEntry(entryId, payload) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/move`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '移动网盘条目')
  return { data: data || {}, traceId }
}

export async function trashDriveEntry(entryId) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/trash`)
  const { data, traceId } = unwrapResultBody(resp.data, '删除网盘条目')
  return { data: data || {}, traceId }
}

export async function restoreDriveEntry(entryId, payload = {}) {
  const resp = await http.post(`/api/drive/trash/${encodeURIComponent(entryId)}/restore`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '恢复网盘条目')
  return { data: data || {}, traceId }
}

export async function deleteDriveEntryPermanently(entryId) {
  const resp = await http.delete(`/api/drive/trash/${encodeURIComponent(entryId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '彻底删除网盘条目')
  return { data: data || {}, traceId }
}

export async function getDriveDownloadUrl(entryId) {
  const resp = await http.get(`/api/drive/entries/${encodeURIComponent(entryId)}/download-url`)
  const { data, traceId } = unwrapResultBody(resp.data, '获取网盘下载链接')
  return { data: data || {}, traceId }
}

export async function createDriveShare(entryId, payload) {
  const resp = await http.post(`/api/drive/entries/${encodeURIComponent(entryId)}/shares`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建网盘分享')
  return { data: data || {}, traceId }
}

export async function revokeDriveShare(shareId) {
  const resp = await http.delete(`/api/drive/shares/${encodeURIComponent(shareId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '撤销网盘分享')
  return { data: data || {}, traceId }
}

export async function getPublicDriveShare(shareToken) {
  const resp = await http.get(`/api/drive/shares/${encodeURIComponent(shareToken)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '获取公开分享信息')
  return { data: normalizePublicShareGate(data || {}), traceId }
}

function normalizePublicShareGate(raw) {
  return {
    shareToken: String(raw?.shareToken || ''),
    requiresPassword: raw?.requiresPassword !== false
  }
}

export async function verifyDriveShare(shareToken, password) {
  const resp = await http.post(`/api/drive/shares/${encodeURIComponent(shareToken)}/verify`, { password: String(password || '') })
  const { data, traceId } = unwrapResultBody(resp.data, '校验分享提取码')
  return { data: data || {}, traceId }
}

export async function listDriveShareEntries(shareToken, ticket, parentId = '') {
  const resp = await http.get(`/api/drive/shares/${encodeURIComponent(shareToken)}/entries`, {
    params: { ticket, parentId }
  })
  const { data, traceId } = unwrapResultBody(resp.data, '查询分享文件')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getDriveShareDownloadUrl(shareToken, ticket, entryId = '') {
  const resp = await http.get(`/api/drive/shares/${encodeURIComponent(shareToken)}/download-url`, {
    params: { ticket, entryId }
  })
  const { data, traceId } = unwrapResultBody(resp.data, '获取分享下载链接')
  return { data: data || {}, traceId }
}
