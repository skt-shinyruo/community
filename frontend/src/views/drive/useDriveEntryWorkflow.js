// @ts-check
import { computed, reactive, ref } from 'vue'
import {
  createDriveFolder,
  deleteDriveEntryPermanently,
  getDriveDownloadUrl,
  getDriveSpace,
  listDriveEntries,
  listDriveTrash,
  moveDriveEntry,
  renameDriveEntry,
  restoreDriveEntry,
  searchDriveEntries,
  trashDriveEntry
} from '../../api/services/driveService'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { settleNamedRequests } from '../../utils/settledRequests'
import { normalizeDriveEntry, normalizeDriveQuota } from '../driveState'

const DEFAULT_QUOTA_BYTES = 10 * 1024 * 1024 * 1024

function defaultDriveSpace() {
  return { quotaBytes: DEFAULT_QUOTA_BYTES, usedBytes: 0, remainingBytes: DEFAULT_QUOTA_BYTES }
}

export function useDriveEntryWorkflow({ workspace, session, runAction, reloadPage, confirm }) {
  const requestTracker = createLatestRequestTracker()
  const space = ref(defaultDriveSpace())
  const folderNameDraft = ref('')
  const creatingFolder = ref(false)
  const folderError = ref('')
  const renameError = ref('')
  const quota = computed(() => normalizeDriveQuota(space.value))

  async function refresh() {
    const token = requestTracker.begin()
    const scope = session.capture()
    const requestedMode = workspace.mode.value
    const requestedFolderId = workspace.currentFolderId.value
    const requestedKeyword = String(workspace.searchKeyword.value || '').trim()
    const entryRequest = requestedMode === 'trash'
      ? () => listDriveTrash()
      : requestedKeyword
        ? () => searchDriveEntries({ keyword: requestedKeyword })
        : () => listDriveEntries({ parentId: requestedFolderId })
    const outcome = await settleNamedRequests({
      space: () => getDriveSpace(),
      entries: entryRequest
    })
    if (!requestTracker.isCurrent(token) || !session.isCurrent(scope)) return { stale: true }

    if (outcome.results.space.ok) space.value = outcome.results.space.value?.data || {}
    if (outcome.results.entries.ok) {
      const response = outcome.results.entries.value
      const list = Array.isArray(response?.data) ? response.data.map(normalizeDriveEntry) : []
      workspace.commitEntries(requestedMode === 'trash' ? workspace.trashEntries : workspace.entries, list)
    }
    return {
      stale: false,
      successCount: ['space', 'entries'].filter((key) => outcome.results[key]?.ok).length,
      failures: outcome.failedKeys.map((key) => outcome.results[key]?.error)
    }
  }

  function toggleFolderComposer() {
    creatingFolder.value = !creatingFolder.value
    folderError.value = ''
    if (creatingFolder.value) folderNameDraft.value = ''
  }

  function cancelCreateFolder() {
    creatingFolder.value = false
    folderNameDraft.value = ''
    folderError.value = ''
  }

  async function createFolder() {
    const name = String(folderNameDraft.value || '').trim()
    if (!name) {
      folderError.value = '请输入文件夹名称'
      return
    }
    await runAction('folder', async (request) => {
      await createDriveFolder({ parentId: workspace.currentFolderId.value, name })
      if (!request.isCurrent()) return
      folderNameDraft.value = ''
      creatingFolder.value = false
      folderError.value = ''
      // 结果立即可见（新文件夹随列表刷新出现），静默更新，不弹 toast。
      await reloadPage()
    }, { onError: (message) => { folderError.value = message } }).catch(() => {})
  }

  async function renameSelected() {
    const entry = workspace.selectedEntry.value
    const newName = String(workspace.renameDraft.value || '').trim()
    if (!entry) return
    if (!newName) {
      renameError.value = '请输入新名称'
      return
    }
    await runAction('rename', async (request) => {
      await renameDriveEntry(entry.entryId, { newName })
      if (!request.isCurrent()) return
      renameError.value = ''
      await reloadPage()
    }, { onError: (message) => { renameError.value = message } }).catch(() => {})
  }

  async function moveSelectedHere() {
    const entry = workspace.selectedEntry.value
    if (!entry) return
    await runAction('move', async (request) => {
      await moveDriveEntry(entry.entryId, { targetParentId: workspace.currentFolderId.value })
      if (!request.isCurrent()) return
      await reloadPage()
    }).catch(() => {})
  }

  async function downloadSelected() {
    const entry = workspace.selectedEntry.value
    if (!entry?.canDownload) return
    await runAction('download', async (request) => {
      const { data: rawData } = await getDriveDownloadUrl(entry.entryId)
      if (!request.isCurrent()) return
      const data = rawData && typeof rawData === 'object'
        ? /** @type {Record<string, any>} */ (rawData)
        : {}
      if (data?.url && typeof window !== 'undefined') window.open(data.url, '_blank', 'noopener,noreferrer')
    }).catch(() => {})
  }

  // 删除与彻底删除走 UiModalConfirm 二次确认（confirm 由页面层注入）；
  // 移至回收站可恢复，彻底删除不可逆，两者文案与危险级别在确认弹窗中区分。
  function trashSelected() {
    const entry = workspace.selectedEntry.value
    if (!entry?.canTrash) return
    confirm({
      title: '删除到回收站',
      message: `「${entry.name}」将移至回收站，之后可以在回收站恢复。`,
      confirmText: '移至回收站',
      variant: 'danger'
    }, () => trashEntry(entry))
  }

  async function trashEntry(entry) {
    await runAction('trash', async (request) => {
      await trashDriveEntry(entry.entryId)
      if (!request.isCurrent()) return
      await reloadPage()
    }).catch(() => {})
  }

  async function restore(entry) {
    if (!entry?.canRestore) return
    await runAction('restore', async (request) => {
      await restoreDriveEntry(entry.entryId, { targetParentId: workspace.currentFolderId.value })
      if (!request.isCurrent()) return
      await reloadPage()
    }).catch(() => {})
  }

  function deleteSelectedPermanently() {
    const entry = workspace.selectedEntry.value
    if (!entry?.canDeletePermanently) return
    confirm({
      title: '彻底删除',
      message: `「${entry.name}」将被永久删除，无法恢复。`,
      confirmText: '彻底删除',
      variant: 'danger'
    }, () => deletePermanently(entry))
  }

  async function deletePermanently(entry) {
    await runAction('delete', async (request) => {
      await deleteDriveEntryPermanently(entry.entryId)
      if (!request.isCurrent()) return
      await reloadPage()
    }).catch(() => {})
  }

  function reset() {
    requestTracker.invalidate()
    space.value = defaultDriveSpace()
    folderNameDraft.value = ''
    creatingFolder.value = false
    folderError.value = ''
    renameError.value = ''
  }

  const model = reactive({
    quota,
    folderNameDraft,
    creatingFolder,
    folderError,
    renameError,
    toggleFolderComposer,
    cancelCreateFolder,
    createFolder,
    renameSelected,
    moveSelectedHere,
    downloadSelected,
    trashSelected,
    restore,
    restoreSelected: () => restore(workspace.selectedEntry.value),
    deleteSelectedPermanently
  })

  return { model, refresh, reset, invalidate: () => requestTracker.invalidate() }
}
