import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/services/driveService', () => ({
  createDriveFolder: vi.fn(),
  deleteDriveEntryPermanently: vi.fn(),
  getDriveDownloadUrl: vi.fn(),
  getDriveSpace: vi.fn(),
  listDriveEntries: vi.fn(),
  listDriveTrash: vi.fn(),
  moveDriveEntry: vi.fn(),
  renameDriveEntry: vi.fn(),
  restoreDriveEntry: vi.fn(),
  searchDriveEntries: vi.fn(),
  trashDriveEntry: vi.fn()
}))

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
import { useDriveEntryWorkflow } from './useDriveEntryWorkflow'
import { useDriveWorkspaceState } from './useDriveWorkspaceState'

describe('useDriveEntryWorkflow', () => {
  function createSubject({ current = true } = {}) {
    const workspace = useDriveWorkspaceState()
    const setError = vi.fn()
    const setStatus = vi.fn()
    const reloadPage = vi.fn().mockResolvedValue(undefined)
    const session = {
      capture: vi.fn(() => ({ generation: 1 })),
      isCurrent: vi.fn(() => current)
    }
    const runAction = vi.fn(async (label, action) => action({ isCurrent: () => current }))
    const workflow = useDriveEntryWorkflow({
      workspace,
      session,
      runAction,
      reloadPage,
      setError,
      setStatus
    })
    return { workspace, workflow, runAction, reloadPage, session, setError, setStatus }
  }

  beforeEach(() => {
    vi.clearAllMocks()
    getDriveSpace.mockResolvedValue({
      data: { quotaBytes: 1000, usedBytes: 250, remainingBytes: 750 }
    })
    listDriveEntries.mockResolvedValue({ data: [] })
    listDriveTrash.mockResolvedValue({ data: [] })
    searchDriveEntries.mockResolvedValue({ data: [] })
    createDriveFolder.mockResolvedValue({ data: {} })
    renameDriveEntry.mockResolvedValue({ data: {} })
    moveDriveEntry.mockResolvedValue({ data: {} })
    getDriveDownloadUrl.mockResolvedValue({ data: { url: 'https://files.example.test/download' } })
    trashDriveEntry.mockResolvedValue({ data: {} })
    restoreDriveEntry.mockResolvedValue({ data: {} })
    deleteDriveEntryPermanently.mockResolvedValue({ data: {} })
  })

  it('loads files, search results, and trash into their owning workspace collections', async () => {
    const { workspace, workflow } = createSubject()
    listDriveEntries.mockResolvedValueOnce({
      data: [{ entryId: 'file-1', name: 'guide.pdf', type: 'FILE', status: 'ACTIVE' }]
    })

    await expect(workflow.refresh()).resolves.toMatchObject({ stale: false, successCount: 2, failures: [] })
    expect(listDriveEntries).toHaveBeenCalledWith({ parentId: '' })
    expect(workspace.entries.value[0]).toMatchObject({ entryId: 'file-1', canDownload: true })
    expect(workflow.model.quota).toMatchObject({ quotaBytes: 1000, usedBytes: 250 })

    workspace.searchKeyword.value = ' report '
    searchDriveEntries.mockResolvedValueOnce({
      data: [{ entryId: 'file-2', name: 'report.csv', type: 'FILE', status: 'ACTIVE' }]
    })
    await workflow.refresh()
    expect(searchDriveEntries).toHaveBeenCalledWith({ keyword: 'report' })
    expect(workspace.entries.value[0].entryId).toBe('file-2')

    workspace.mode.value = 'trash'
    workspace.searchKeyword.value = ''
    listDriveTrash.mockResolvedValueOnce({
      data: [{ entryId: 'file-3', name: 'old.txt', type: 'FILE', status: 'TRASHED' }]
    })
    await workflow.refresh()
    expect(listDriveTrash).toHaveBeenCalledTimes(1)
    expect(workspace.trashEntries.value[0]).toMatchObject({ entryId: 'file-3', canRestore: true })
  })

  it('reports partial failures and discards a refresh from an expired session', async () => {
    const current = createSubject()
    getDriveSpace.mockRejectedValueOnce(new Error('quota unavailable'))
    listDriveEntries.mockResolvedValueOnce({ data: [{ entryId: 'file-1', name: 'kept.txt' }] })

    await expect(current.workflow.refresh()).resolves.toMatchObject({
      stale: false,
      successCount: 1,
      failures: [expect.objectContaining({ message: 'quota unavailable' })]
    })
    expect(current.workspace.entries.value[0].entryId).toBe('file-1')

    const stale = createSubject({ current: false })
    listDriveEntries.mockResolvedValueOnce({ data: [{ entryId: 'stale', name: 'stale.txt' }] })
    await expect(stale.workflow.refresh()).resolves.toEqual({ stale: true })
    expect(stale.workspace.entries.value).toEqual([])
  })

  it('validates folder and rename drafts before running writes', async () => {
    const { workspace, workflow, runAction, reloadPage, setError, setStatus } = createSubject()

    workflow.model.toggleFolderComposer()
    expect(workflow.model.creatingFolder).toBe(true)
    await workflow.model.createFolder()
    expect(setError).toHaveBeenCalledWith('请输入文件夹名称')
    expect(runAction).not.toHaveBeenCalled()

    workflow.model.folderNameDraft = '  资料  '
    await workflow.model.createFolder()
    expect(createDriveFolder).toHaveBeenCalledWith({ parentId: '', name: '资料' })
    expect(setStatus).toHaveBeenCalledWith('文件夹已创建')
    expect(reloadPage).toHaveBeenCalledTimes(1)
    expect(workflow.model.creatingFolder).toBe(false)

    workspace.commitEntries(workspace.entries, [{ entryId: 'file-1', name: 'old.txt' }])
    workspace.renameDraft.value = '  '
    await workflow.model.renameSelected()
    expect(setError).toHaveBeenCalledWith('请输入新名称')

    workspace.renameDraft.value = ' new.txt '
    await workflow.model.renameSelected()
    expect(renameDriveEntry).toHaveBeenCalledWith('file-1', { newName: 'new.txt' })
    expect(setStatus).toHaveBeenCalledWith('名称已更新')

    workflow.model.toggleFolderComposer()
    workflow.model.folderNameDraft = 'discarded'
    workflow.model.cancelCreateFolder()
    expect(workflow.model.folderNameDraft).toBe('')
  })

  it('executes move, download, trash, restore, and permanent-delete capabilities', async () => {
    const { workspace, workflow, reloadPage, setStatus } = createSubject()
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    const active = {
      entryId: 'file-1',
      name: 'active.txt',
      canDownload: true,
      canTrash: true
    }
    workspace.commitEntries(workspace.entries, [active])

    await workflow.model.moveSelectedHere()
    await workflow.model.downloadSelected()
    await workflow.model.trashSelected()
    expect(moveDriveEntry).toHaveBeenCalledWith('file-1', { targetParentId: '' })
    expect(open).toHaveBeenCalledWith('https://files.example.test/download', '_blank', 'noopener,noreferrer')
    expect(trashDriveEntry).toHaveBeenCalledWith('file-1')

    const trashed = {
      entryId: 'file-2',
      name: 'trashed.txt',
      canRestore: true,
      canDeletePermanently: true
    }
    workspace.mode.value = 'trash'
    workspace.commitEntries(workspace.trashEntries, [trashed])
    await workflow.model.restoreSelected()
    await workflow.model.deleteSelectedPermanently()
    expect(restoreDriveEntry).toHaveBeenCalledWith('file-2', { targetParentId: '' })
    expect(deleteDriveEntryPermanently).toHaveBeenCalledWith('file-2')
    expect(setStatus.mock.calls.map(([message]) => message)).toEqual(expect.arrayContaining([
      '条目已移动',
      '条目已移至回收站',
      '条目已恢复',
      '条目已彻底删除'
    ]))
    expect(reloadPage).toHaveBeenCalledTimes(4)
  })

  it('suppresses stale write effects and resets local entry state', async () => {
    const { workspace, workflow, reloadPage, setStatus } = createSubject({ current: false })
    workspace.commitEntries(workspace.entries, [{
      entryId: 'file-1',
      name: 'active.txt',
      canDownload: true,
      canTrash: true
    }])
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)

    await workflow.model.downloadSelected()
    await workflow.model.trashSelected()
    expect(open).not.toHaveBeenCalled()
    expect(setStatus).not.toHaveBeenCalled()
    expect(reloadPage).not.toHaveBeenCalled()

    workflow.model.toggleFolderComposer()
    workflow.model.folderNameDraft = 'temporary'
    workflow.reset()
    expect(workflow.model.creatingFolder).toBe(false)
    expect(workflow.model.folderNameDraft).toBe('')
    expect(workflow.model.quota).toMatchObject({ usedBytes: 0 })
    workflow.invalidate()
  })
})
