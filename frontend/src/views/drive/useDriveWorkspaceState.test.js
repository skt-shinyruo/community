import { describe, expect, it } from 'vitest'
import { useDriveWorkspaceState } from './useDriveWorkspaceState'

describe('useDriveWorkspaceState', () => {
  it('owns folder navigation, selection, and mode resets as one state boundary', () => {
    const workspace = useDriveWorkspaceState()
    const folder = { entryId: 'folder-1', name: '资料', isFolder: true }
    const file = { entryId: 'file-1', name: 'readme.txt', isFolder: false }

    workspace.commitEntries(workspace.entries, [folder, file])
    expect(workspace.selectedEntry.value).toEqual(folder)
    expect(workspace.enterFolder(folder)).toBe(true)
    expect(workspace.currentFolderId.value).toBe('folder-1')
    expect(workspace.selectedEntry.value).toBeNull()

    workspace.searchKeyword.value = 'readme'
    expect(workspace.switchMode('trash')).toBe(true)
    expect(workspace.searchKeyword.value).toBe('')
    expect(workspace.goBreadcrumb(0)).toBe(true)
    expect(workspace.currentFolderId.value).toBe('')
    expect(workspace.mode.value).toBe('files')
  })

  it('preserves a valid selection across entry refreshes and falls back deterministically', () => {
    const workspace = useDriveWorkspaceState()
    const first = { entryId: 'file-1', name: 'first.txt' }
    const second = { entryId: 'file-2', name: 'second.txt' }
    workspace.commitEntries(workspace.entries, [first, second])
    workspace.selectEntry(second)

    const renamedSecond = { ...second, name: 'renamed.txt' }
    workspace.commitEntries(workspace.entries, [renamedSecond])
    expect(workspace.selectedEntryId.value).toBe('file-2')
    expect(workspace.renameDraft.value).toBe('renamed.txt')

    workspace.commitEntries(workspace.entries, [first])
    expect(workspace.selectedEntryId.value).toBe('file-1')
  })
})
