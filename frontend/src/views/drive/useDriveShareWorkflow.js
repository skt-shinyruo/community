// @ts-check
import { reactive, ref } from 'vue'
import { createDriveShare, listDriveShares, revokeDriveShare } from '../../api/services/driveService'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { normalizeCreatedDriveShare, validateShareForm } from '../driveState'

const ONE_DAY_MS = 24 * 60 * 60 * 1000
const SHARE_PAGE_SIZE = 20

/** @typedef {{ shareId?: string, shareToken?: string, shareUrl?: string, [key: string]: any }} DriveShare */

function toDatetimeLocalValue(date) {
  const safe = date instanceof Date ? date : new Date(date)
  const year = safe.getFullYear()
  const month = String(safe.getMonth() + 1).padStart(2, '0')
  const day = String(safe.getDate()).padStart(2, '0')
  const hour = String(safe.getHours()).padStart(2, '0')
  const minute = String(safe.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function buildShareUrl(shareToken) {
  if (typeof window === 'undefined') return `#/drive/s/${shareToken}`
  return `${window.location.origin}/#/drive/s/${shareToken}`
}

function statusLabel(status) {
  const normalized = String(status || '').trim().toUpperCase()
  if (normalized === 'ACTIVE') return '有效'
  if (normalized === 'EXPIRED') return '已过期'
  if (normalized === 'REVOKED') return '已撤销'
  return '状态待确认'
}

export function useDriveShareWorkflow({ workspace, session, runAction, reloadPage, confirm, notify }) {
  const requestTracker = createLatestRequestTracker()
  const password = ref('')
  const expiresAt = ref(toDatetimeLocalValue(new Date(Date.now() + ONE_DAY_MS)))
  const error = ref('')
  const items = ref(/** @type {DriveShare[]} */ ([]))
  const page = ref(0)
  const hasNext = ref(false)

  function normalizeShare(data) {
    const share = /** @type {DriveShare} */ (normalizeCreatedDriveShare(data))
    return { ...share, shareUrl: buildShareUrl(share.shareToken) }
  }

  async function loadPage({ reset = false } = {}) {
    const token = requestTracker.begin()
    const scope = session.capture()
    const targetPage = reset ? 0 : page.value + 1
    let data
    try {
      data = (await listDriveShares({ page: targetPage, size: SHARE_PAGE_SIZE }))?.data
    } catch (cause) {
      if (!requestTracker.isCurrent(token) || !session.isCurrent(scope) || workspace.mode.value !== 'shares') {
        return { stale: true }
      }
      throw cause
    }
    if (!requestTracker.isCurrent(token) || !session.isCurrent(scope) || workspace.mode.value !== 'shares') {
      return { stale: true }
    }
    const nextItems = (Array.isArray(data?.items) ? data.items : []).map(normalizeShare)
    items.value = reset ? nextItems : [...items.value, ...nextItems]
    page.value = targetPage
    hasNext.value = data?.hasNext === true
    return { stale: false, successCount: 1, failures: [] }
  }

  async function refresh() {
    try {
      return await loadPage({ reset: true })
    } catch (cause) {
      return { stale: false, successCount: 0, failures: [cause] }
    }
  }

  async function open() {
    workspace.mode.value = 'shares'
    error.value = ''
    await reloadPage()
  }

  async function loadMore() {
    if (!hasNext.value) return
    error.value = ''
    // 加载更多失败可定位到分享区块，保留已加载分页并内联报错。
    await runAction('shares', () => loadPage(), {
      onError: (message) => { error.value = message }
    }).catch(() => {})
  }

  async function create() {
    const entry = workspace.selectedEntry.value
    error.value = ''
    if (!entry?.canShare) {
      error.value = '请选择可分享的文件或文件夹'
      return
    }
    const validation = validateShareForm({ password: password.value, expiresAt: expiresAt.value }, new Date())
    if (!validation.valid) {
      error.value = validation.message
      return
    }
    await runAction('share', async (request) => {
      await createDriveShare(entry.entryId, {
        password: String(password.value || '').trim(),
        expiresAt: new Date(expiresAt.value).toISOString()
      })
      if (!request.isCurrent()) return
      password.value = ''
      expiresAt.value = toDatetimeLocalValue(new Date(Date.now() + ONE_DAY_MS))
      // 结果立即可见（新分享出现在分享列表顶部），静默更新，不弹 toast。
      workspace.mode.value = 'shares'
      await loadPage({ reset: true })
    }, { onError: (message) => { error.value = message } }).catch(() => {})
  }

  // 撤销影响已经拿到链接的访问者，走 UiModalConfirm 二次确认。
  function revoke(item) {
    if (!item?.shareId) return
    confirm({
      title: '撤销分享',
      message: `撤销后「${item.entryName || '该条目'}」的分享链接立即失效，已拿到链接的人无法继续访问。`,
      confirmText: '撤销',
      variant: 'danger'
    }, () => revokeShare(item))
  }

  async function revokeShare(item) {
    await runAction('revoke', async (request) => {
      await revokeDriveShare(item.shareId)
      if (!request.isCurrent()) return
      await loadPage({ reset: true })
    }, { onError: (message) => { error.value = message } }).catch(() => {})
  }

  async function copy(item) {
    if (!item?.shareUrl) return
    const scope = session.capture()
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(item.shareUrl)
      if (session.isCurrent(scope)) notify('分享链接已复制')
      return
    }
    if (session.isCurrent(scope)) notify(item.shareUrl)
  }

  function reset() {
    requestTracker.invalidate()
    password.value = ''
    expiresAt.value = toDatetimeLocalValue(new Date(Date.now() + ONE_DAY_MS))
    error.value = ''
    items.value = []
    page.value = 0
    hasNext.value = false
  }

  const model = reactive({ password, expiresAt, error, items, hasNext, open, loadMore, create, revoke, copy, statusLabel })
  return { model, refresh, reset, invalidate: () => requestTracker.invalidate() }
}
