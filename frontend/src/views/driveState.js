export function formatDriveBytes(value) {
  const bytes = Math.max(0, Number(value || 0))
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let n = bytes / 1024
  let unit = units[0]
  for (let i = 1; i < units.length && n >= 1024; i += 1) {
    n /= 1024
    unit = units[i]
  }
  return `${Number.isInteger(n) ? n : n.toFixed(1)} ${unit}`
}

export function normalizeDriveQuota(raw = {}) {
  const quotaBytes = Number(raw.quotaBytes || 0)
  const usedBytes = Number(raw.usedBytes || 0)
  const remainingBytes = Math.max(0, Number(raw.remainingBytes ?? quotaBytes - usedBytes))
  const usedPercent = quotaBytes > 0 ? Math.min(100, Math.round((usedBytes / quotaBytes) * 100)) : 0
  return {
    quotaBytes,
    usedBytes,
    remainingBytes,
    usedPercent,
    label: `${formatDriveBytes(usedBytes)} / ${formatDriveBytes(quotaBytes)}`
  }
}

export function buildDriveBreadcrumb(ancestors = []) {
  return [
    { entryId: '', name: '我的文件' },
    ...ancestors.map((it) => ({ entryId: String(it.entryId || ''), name: String(it.name || '') }))
  ]
}

export function normalizeDriveEntry(raw = {}) {
  const status = String(raw.status || 'ACTIVE').toUpperCase()
  const type = String(raw.type || 'FILE').toUpperCase()
  const active = status === 'ACTIVE'
  return {
    ...raw,
    entryId: String(raw.entryId || ''),
    parentId: String(raw.parentId || ''),
    name: String(raw.name || ''),
    type,
    status,
    sizeBytes: Number(raw.sizeBytes || 0),
    isFolder: type === 'FOLDER',
    isFile: type === 'FILE',
    canDownload: active && type === 'FILE',
    canShare: active,
    canRename: active,
    canMove: active,
    canTrash: active,
    canRestore: status === 'TRASHED',
    canDeletePermanently: status === 'TRASHED'
  }
}

export function validateShareForm(form = {}, now = new Date()) {
  if (!String(form.password || '').trim()) return { valid: false, message: '请输入提取码' }
  const expires = new Date(form.expiresAt || '')
  if (!Number.isFinite(expires.getTime()) || expires <= now) return { valid: false, message: '有效期必须晚于当前时间' }
  return { valid: true, message: '' }
}

export function reduceDriveSelection(selectedEntryId, entries = []) {
  const selected = String(selectedEntryId || '')
  return entries.some((it) => String(it.entryId || '') === selected) ? selected : ''
}
