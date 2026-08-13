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

export function useDriveEntryWorkflow({ workspace, session, runAction, reloadPage, setError, setStatus }) {
  const requestTracker = createLatestRequestTracker()
  const space = ref(defaultDriveSpace())
  const folderNameDraft = ref('')
  const creatingFolder = ref(false)
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
    if (creatingFolder.value) folderNameDraft.value = ''
  }

  function cancelCreateFolder() {
    creatingFolder.value = false
    folderNameDraft.value = ''
  }

  async function createFolder() {
    const name = String(folderNameDraft.value || '').trim()
    if (!name) {
      setError('请输入文件夹名称')
      return
    }
    await runAction('folder', async (request) => {
      await createDriveFolder({ parentId: workspace.currentFolderId.value, name })
      if (!request.isCurrent()) return
      folderNameDraft.value = ''
      creatingFolder.value = false
      setStatus('文件夹已创建')
      await reloadPage()
    }).catch(() => {})
  }

  async function renameSelected() {
    const entry = workspace.selectedEntry.value
    const newName = String(workspace.renameDraft.value || '').trim()
    if (!entry) return
    if (!newName) {
      setError('请输入新名称')
      return
    }
    await runAction('rename', async (request) => {
      await renameDriveEntry(entry.entryId, { newName })
      if (!request.isCurrent()) return
      setStatus('名称已更新')
      await reloadPage()
    }).catch(() => {})
  }

  async function moveSelectedHere() {
    const entry = workspace.selectedEntry.value
    if (!entry) return
    await runAction('move', async (request) => {
      await moveDriveEntry(entry.entryId, { targetParentId: workspace.currentFolderId.value })
      if (!request.isCurrent()) return
      setStatus('条目已移动')
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

  async function trashSelected() {
    const entry = workspace.selectedEntry.value
    if (!entry?.canTrash) return
    await runAction('trash', async (request) => {
      await trashDriveEntry(entry.entryId)
      if (!request.isCurrent()) return
      setStatus('条目已移至回收站')
      await reloadPage()
    }).catch(() => {})
  }

  async function restore(entry) {
    if (!entry?.canRestore) return
    await runAction('restore', async (request) => {
      await restoreDriveEntry(entry.entryId, { targetParentId: workspace.currentFolderId.value })
      if (!request.isCurrent()) return
      setStatus('条目已恢复')
      await reloadPage()
    }).catch(() => {})
  }

  async function deleteSelectedPermanently() {
    const entry = workspace.selectedEntry.value
    if (!entry?.canDeletePermanently) return
    await runAction('delete', async (request) => {
      await deleteDriveEntryPermanently(entry.entryId)
      if (!request.isCurrent()) return
      setStatus('条目已彻底删除')
      await reloadPage()
    }).catch(() => {})
  }

  function reset() {
    requestTracker.invalidate()
    space.value = defaultDriveSpace()
    folderNameDraft.value = ''
    creatingFolder.value = false
  }

  const model = reactive({
    quota,
    folderNameDraft,
    creatingFolder,
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
