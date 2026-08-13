// @ts-check
import { computed, ref } from 'vue'
import { buildDriveBreadcrumb, reduceDriveSelection } from '../driveState'

/** @typedef {{ entryId: string, name: string, isFolder?: boolean, [key: string]: any }} DriveEntry */
/** @type {Readonly<{ entryId: string, name: string }>} */
const ROOT_FOLDER = Object.freeze({ entryId: '', name: '我的文件' })

export function useDriveWorkspaceState() {
  const mode = ref('files')
  const entries = ref(/** @type {DriveEntry[]} */ ([]))
  const trashEntries = ref(/** @type {DriveEntry[]} */ ([]))
  const selectedEntryId = ref('')
  const folderTrail = ref(/** @type {Array<{ entryId: string, name: string }>} */ ([{ ...ROOT_FOLDER }]))
  const searchKeyword = ref('')
  const renameDraft = ref('')

  const currentFolderId = computed(() => folderTrail.value.at(-1)?.entryId || '')
  const currentFolderLabel = computed(() => folderTrail.value.map((item) => item.name).join(' / '))
  const breadcrumbItems = computed(() => buildDriveBreadcrumb(folderTrail.value.slice(1)))
  const visibleEntries = computed(() => mode.value === 'trash' ? trashEntries.value : entries.value)
  const selectedEntry = computed(() =>
    visibleEntries.value.find((item) => item.entryId === selectedEntryId.value) || null
  )

  function clearSelection() {
    selectedEntryId.value = ''
    renameDraft.value = ''
  }

  function reset() {
    mode.value = 'files'
    entries.value = []
    trashEntries.value = []
    folderTrail.value = [{ ...ROOT_FOLDER }]
    searchKeyword.value = ''
    clearSelection()
  }

  function selectEntry(entry) {
    selectedEntryId.value = String(entry?.entryId || '')
    renameDraft.value = String(entry?.name || '')
  }

  function commitEntries(target, list) {
    const nextList = Array.isArray(list) ? list : []
    target.value = nextList
    selectedEntryId.value = reduceDriveSelection(selectedEntryId.value, nextList) || (nextList[0]?.entryId || '')
    renameDraft.value = selectedEntryId.value
      ? String(nextList.find((item) => item.entryId === selectedEntryId.value)?.name || '')
      : ''
  }

  function switchMode(nextMode) {
    const next = String(nextMode || '')
    if (!['files', 'shares', 'trash'].includes(next) || mode.value === next) return false
    if (next !== 'files') searchKeyword.value = ''
    mode.value = next
    clearSelection()
    return true
  }

  function beginSearch() {
    mode.value = 'files'
    clearSelection()
  }

  function enterFolder(entry) {
    if (!entry?.isFolder) return false
    mode.value = 'files'
    searchKeyword.value = ''
    folderTrail.value = [
      ...folderTrail.value,
      { entryId: String(entry.entryId), name: String(entry.name || '') }
    ]
    clearSelection()
    return true
  }

  function goBreadcrumb(index) {
    if (!Number.isInteger(index) || index < 0 || index >= breadcrumbItems.value.length) return false
    mode.value = 'files'
    searchKeyword.value = ''
    folderTrail.value = index === 0
      ? [{ ...ROOT_FOLDER }]
      : folderTrail.value.slice(0, index + 1)
    clearSelection()
    return true
  }

  return {
    mode,
    entries,
    trashEntries,
    selectedEntryId,
    folderTrail,
    searchKeyword,
    renameDraft,
    currentFolderId,
    currentFolderLabel,
    breadcrumbItems,
    visibleEntries,
    selectedEntry,
    reset,
    selectEntry,
    commitEntries,
    switchMode,
    beginSearch,
    enterFolder,
    goBreadcrumb
  }
}
