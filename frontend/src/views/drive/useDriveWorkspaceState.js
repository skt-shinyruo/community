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
  // 全局搜索命中可以直接进入任意层级的文件夹，而后端不返回祖先链：
  // pathKnown=false 表示 folderTrail 只记录当前位置，面包屑隐藏未知前缀而不是虚构路径。
  const pathKnown = ref(true)
  const searchKeyword = ref('')
  const renameDraft = ref('')

  const currentFolderId = computed(() => folderTrail.value.at(-1)?.entryId || '')
  const currentFolderLabel = computed(() => {
    if (!pathKnown.value) return folderTrail.value.at(-1)?.name || ROOT_FOLDER.name
    return folderTrail.value.map((item) => item.name).join(' / ')
  })
  const breadcrumbItems = computed(() => buildDriveBreadcrumb(folderTrail.value.slice(1)))
  // 供 UiBreadcrumb 受控模式消费的导航项：trailIndex 指向 folderTrail 下标，null 表示不可导航（省略占位 / 当前位置）。
  const breadcrumbNav = computed(() => {
    if (pathKnown.value) {
      return breadcrumbItems.value.map((item, index) => ({ label: item.name, trailIndex: index }))
    }
    return [
      { label: ROOT_FOLDER.name, trailIndex: 0 },
      { label: '…', trailIndex: null },
      { label: breadcrumbItems.value.at(-1)?.name || ROOT_FOLDER.name, trailIndex: null }
    ]
  })
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
    pathKnown.value = true
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
    const fromSearch = Boolean(String(searchKeyword.value || '').trim())
    const target = { entryId: String(entry.entryId), name: String(entry.name || '') }
    mode.value = 'files'
    searchKeyword.value = ''
    if (!fromSearch || !pathKnown.value) {
      // 目录内逐级进入：续接的是真实父子关系；路径未知时继续保持未知。
      folderTrail.value = [...folderTrail.value, target]
    } else {
      // 全局搜索命中：仅当能确认真实父级时才续接路径，否则重置为可确认的根级路径或标记路径未知。
      const parentId = String(entry.parentId || '')
      if (parentId === currentFolderId.value) {
        folderTrail.value = [...folderTrail.value, target]
      } else {
        folderTrail.value = [{ ...ROOT_FOLDER }, target]
        pathKnown.value = !parentId
      }
    }
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
    if (index === 0) pathKnown.value = true
    clearSelection()
    return true
  }

  return {
    mode,
    entries,
    trashEntries,
    selectedEntryId,
    folderTrail,
    pathKnown,
    searchKeyword,
    renameDraft,
    currentFolderId,
    currentFolderLabel,
    breadcrumbItems,
    breadcrumbNav,
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
