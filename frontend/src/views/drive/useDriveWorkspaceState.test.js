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

  it('keeps the real hierarchy when a search hit is a direct child of the current folder', () => {
    const workspace = useDriveWorkspaceState()
    const docs = { entryId: 'folder-docs', name: '文档', isFolder: true, parentId: '' }
    const inner = { entryId: 'folder-inner', name: '内部', isFolder: true, parentId: 'folder-docs' }
    workspace.enterFolder(docs)

    workspace.searchKeyword.value = '内部'
    expect(workspace.enterFolder(inner)).toBe(true)

    expect(workspace.pathKnown.value).toBe(true)
    expect(workspace.searchKeyword.value).toBe('')
    expect(workspace.breadcrumbNav.value.map((item) => item.label)).toEqual(['我的文件', '文档', '内部'])
    expect(workspace.currentFolderLabel.value).toBe('我的文件 / 文档 / 内部')
  })

  it('resets to a verified root path when a search hit lives directly under root', () => {
    const workspace = useDriveWorkspaceState()
    const docs = { entryId: 'folder-docs', name: '文档', isFolder: true, parentId: '' }
    workspace.enterFolder(docs)

    const rootHit = { entryId: 'folder-root', name: '根目录文件夹', isFolder: true, parentId: '' }
    workspace.searchKeyword.value = '根目录'
    expect(workspace.enterFolder(rootHit)).toBe(true)

    expect(workspace.pathKnown.value).toBe(true)
    expect(workspace.breadcrumbNav.value.map((item) => item.label)).toEqual(['我的文件', '根目录文件夹'])
    expect(workspace.currentFolderLabel.value).toBe('我的文件 / 根目录文件夹')
  })

  it('elides the unknown prefix instead of fabricating a location for global search hits', () => {
    const workspace = useDriveWorkspaceState()
    const docs = { entryId: 'folder-docs', name: '文档', isFolder: true, parentId: '' }
    workspace.enterFolder(docs)

    // 全局搜索命中了其他分支的文件夹：真实祖先链未知，不虚构「文档 / 别处」路径。
    const elsewhere = { entryId: 'folder-elsewhere', name: '别处', isFolder: true, parentId: 'folder-other' }
    workspace.searchKeyword.value = '别处'
    expect(workspace.enterFolder(elsewhere)).toBe(true)

    expect(workspace.pathKnown.value).toBe(false)
    expect(workspace.currentFolderId.value).toBe('folder-elsewhere')
    expect(workspace.breadcrumbNav.value).toEqual([
      { label: '我的文件', trailIndex: 0 },
      { label: '…', trailIndex: null },
      { label: '别处', trailIndex: null }
    ])
    // 位置标签不虚构「我的文件 / 别处」，只显示当前文件夹名。
    expect(workspace.currentFolderLabel.value).toBe('别处')

    // 进入子文件夹续接真实子级关系，但前缀仍然未知。
    const child = { entryId: 'folder-child', name: '子层', isFolder: true, parentId: 'folder-elsewhere' }
    expect(workspace.enterFolder(child)).toBe(true)
    expect(workspace.pathKnown.value).toBe(false)
    expect(workspace.breadcrumbNav.value.map((item) => item.label)).toEqual(['我的文件', '…', '子层'])

    // 回到根目录后路径重新可验证。
    expect(workspace.goBreadcrumb(0)).toBe(true)
    expect(workspace.pathKnown.value).toBe(true)
    expect(workspace.breadcrumbNav.value.map((item) => item.label)).toEqual(['我的文件'])
  })
})
