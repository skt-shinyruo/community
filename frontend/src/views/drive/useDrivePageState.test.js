// @vitest-environment jsdom

import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'

const { showToast } = vi.hoisted(() => ({
  showToast: vi.fn()
}))

vi.mock('../../ui/toastService', () => ({
  showToast,
  showErrorToast: vi.fn((cause, fallback) => showToast(fallback)),
  setToastHandler: vi.fn()
}))

vi.mock('../../api/services/driveService', () => ({
  getDriveSpace: vi.fn().mockResolvedValue({ data: { quotaBytes: 1000, usedBytes: 250, remainingBytes: 750 } }),
  listDriveEntries: vi.fn().mockResolvedValue({ data: [] }),
  listDriveTrash: vi.fn().mockResolvedValue({ data: [] }),
  searchDriveEntries: vi.fn().mockResolvedValue({ data: [] }),
  createDriveFolder: vi.fn().mockResolvedValue({ data: {} }),
  renameDriveEntry: vi.fn().mockResolvedValue({ data: {} }),
  moveDriveEntry: vi.fn().mockResolvedValue({ data: {} }),
  trashDriveEntry: vi.fn().mockResolvedValue({ data: {} }),
  restoreDriveEntry: vi.fn().mockResolvedValue({ data: {} }),
  deleteDriveEntryPermanently: vi.fn().mockResolvedValue({ data: {} }),
  getDriveDownloadUrl: vi.fn().mockResolvedValue({ data: { url: 'https://files.example.test/download' } }),
  createDriveShare: vi.fn().mockResolvedValue({ data: {} }),
  listDriveShares: vi.fn().mockResolvedValue({ data: { items: [], hasNext: false, page: 0, size: 20 } }),
  revokeDriveShare: vi.fn().mockResolvedValue({ data: {} }),
  createDriveUploadSession: vi.fn(),
  uploadDriveFile: vi.fn()
}))

import {
  getDriveSpace,
  listDriveEntries,
  listDriveShares,
  listDriveTrash,
  searchDriveEntries
} from '../../api/services/driveService'
import { useDrivePageState } from './useDrivePageState'

// 类型别名：vi.mock 替换后的服务函数在本测试里只按 Mock 使用（宽松 payload 不再受真实签名约束）。
const getDriveSpaceMock = /** @type {import('vitest').Mock} */ (getDriveSpace)
const listDriveEntriesMock = /** @type {import('vitest').Mock} */ (listDriveEntries)
const listDriveSharesMock = /** @type {import('vitest').Mock} */ (listDriveShares)
const listDriveTrashMock = /** @type {import('vitest').Mock} */ (listDriveTrash)
const searchDriveEntriesMock = /** @type {import('vitest').Mock} */ (searchDriveEntries)

const USER_ID = '11111111-1111-7111-8111-111111111111'
const OTHER_USER_ID = '22222222-2222-7222-8222-222222222222'

function mountState() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'drive-token',
    me: { userId: USER_ID, username: 'drive-user' }
  })

  /** @type {ReturnType<typeof useDrivePageState> | undefined} */
  let state
  const Harness = defineComponent({
    setup() {
      state = useDrivePageState()
      return () => null
    }
  })
  mount(Harness, { global: { plugins: [pinia] } })
  if (!state) throw new Error('harness state not initialized')
  return state
}

function entry(entryId, name, extra = {}) {
  return { entryId, name, type: 'FILE', status: 'ACTIVE', ...extra }
}

describe('useDrivePageState', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getDriveSpaceMock.mockResolvedValue({ data: { quotaBytes: 1000, usedBytes: 250, remainingBytes: 750 } })
    listDriveEntriesMock.mockResolvedValue({ data: [] })
    listDriveTrashMock.mockResolvedValue({ data: [] })
    searchDriveEntriesMock.mockResolvedValue({ data: [] })
    listDriveSharesMock.mockResolvedValue({ data: { items: [], hasNext: false, page: 0, size: 20 } })
  })

  it('loads the file workspace on mount and keeps the page error clear', async () => {
    listDriveEntriesMock.mockResolvedValueOnce({ data: [entry('file-1', 'guide.pdf')] })

    const state = mountState()
    await flushPromises()

    expect(listDriveEntries).toHaveBeenCalledWith({ parentId: '' })
    expect(state.workspace.visibleEntries[0]).toMatchObject({ entryId: 'file-1' })
    expect(state.page.loading).toBe(false)
    expect(state.page.error).toBe('')
    expect(state.workspace.mode).toBe('files')
    // 分享区块在 files 模式下不随 reload 拉取。
    expect(listDriveShares).not.toHaveBeenCalled()
  })

  it('reports a full load failure on page.error with the first failing section', async () => {
    getDriveSpaceMock.mockRejectedValueOnce(new Error('space unavailable'))
    listDriveEntriesMock.mockRejectedValueOnce(new Error('entries unavailable'))

    const state = mountState()
    await flushPromises()

    expect(state.page.error).toBe('space unavailable')
    expect(state.page.loading).toBe(false)
    expect(state.workspace.visibleEntries).toEqual([])
  })

  it('keeps successful sections when only part of the drive data fails', async () => {
    getDriveSpaceMock.mockResolvedValueOnce({ data: { quotaBytes: 1000, usedBytes: 250, remainingBytes: 750 } })
    listDriveEntriesMock.mockRejectedValueOnce(new Error('entries unavailable'))

    const state = mountState()
    await flushPromises()

    expect(state.page.error).toBe('部分网盘数据加载失败：entries unavailable')
    expect(state.entries.quota).toMatchObject({ quotaBytes: 1000 })
  })

  it('switching to the shares mode reloads both entries and persisted shares', async () => {
    listDriveSharesMock.mockResolvedValueOnce({
      data: {
        items: [{
          shareId: 'share-1',
          shareToken: 'token-1',
          entryId: 'file-1',
          entryName: 'a.txt',
          entryType: 'FILE',
          status: 'ACTIVE',
          expiresAt: '2026-05-10T00:00:00Z'
        }],
        hasNext: false,
        page: 0,
        size: 20
      }
    })
    const state = mountState()
    await flushPromises()
    expect(state.workspace.mode).toBe('files')

    await state.workspace.switchMode('shares')
    await flushPromises()

    expect(state.shares.items).toHaveLength(1)
    expect(state.shares.items[0]).toMatchObject({ shareId: 'share-1', shareUrl: expect.stringContaining('/drive/s/token-1') })
  })

  it('navigating folders requests the child entries of the opened folder', async () => {
    listDriveEntriesMock
      .mockResolvedValueOnce({ data: [entry('folder-1', '文档', { type: 'FOLDER' })] })
      .mockResolvedValueOnce({ data: [entry('file-2', 'inner.txt')] })

    const state = mountState()
    await flushPromises()

    const folder = state.workspace.visibleEntries.find((item) => item.entryId === 'folder-1')
    await state.workspace.enterFolder(folder)
    await flushPromises()

    expect(listDriveEntries).toHaveBeenLastCalledWith({ parentId: 'folder-1' })
    expect(state.workspace.currentFolderLabel).toContain('文档')
  })

  it('searching reloads through the search endpoint and clearing returns to the folder', async () => {
    searchDriveEntriesMock.mockResolvedValueOnce({ data: [entry('file-hit', 'report.csv')] })

    const state = mountState()
    await flushPromises()

    state.workspace.searchKeyword = 'report'
    await state.workspace.search()
    await flushPromises()
    expect(searchDriveEntries).toHaveBeenCalledWith({ keyword: 'report' })
    expect(state.workspace.visibleEntries[0].entryId).toBe('file-hit')

    await state.workspace.clearSearch()
    await flushPromises()
    expect(listDriveEntries).toHaveBeenLastCalledWith({ parentId: '' })
  })

  it('gates trash behind a danger confirmation and reloads after it is confirmed', async () => {
    listDriveEntriesMock.mockResolvedValue({ data: [entry('file-1', 'old.txt')] })
    const state = mountState()
    await flushPromises()

    state.workspace.select(state.workspace.visibleEntries[0])
    expect(state.confirmation.open).toBe(false)

    state.entries.trashSelected()
    expect(state.confirmation.open).toBe(true)
    expect(state.confirmation.title).toBe('删除到回收站')
    expect(state.confirmation.variant).toBe('danger')

    await state.runConfirmation()
    await flushPromises()
    expect(state.confirmation.open).toBe(false)
    expect(listDriveEntriesMock.mock.calls.filter((call) => call[0]?.parentId === '').length).toBeGreaterThanOrEqual(2)
  })

  it('closes the confirmation without running the action on cancel', async () => {
    listDriveEntriesMock.mockResolvedValue({ data: [entry('file-1', 'old.txt')] })
    const state = mountState()
    await flushPromises()

    state.workspace.select(state.workspace.visibleEntries[0])
    state.entries.trashSelected()
    state.closeConfirmation()

    expect(state.confirmation.open).toBe(false)
    expect(state.confirmation.action).toBe(null)
  })

  it('keeps an in-flight action busy and clears busyAction when it settles', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolveCreate
    const { createDriveFolder } = await import('../../api/services/driveService')
    const createDriveFolderMock = /** @type {import('vitest').Mock} */ (createDriveFolder)
    createDriveFolderMock.mockImplementationOnce(() => new Promise((resolve) => { resolveCreate = resolve }))

    const state = mountState()
    await flushPromises()

    state.entries.toggleFolderComposer()
    state.entries.folderNameDraft = '新文件夹'
    const pending = state.entries.createFolder()
    expect(state.page.busyAction).toBe('folder')
    expect(state.page.isBusy).toBe(true)

    await vi.waitFor(() => expect(typeof resolveCreate).toBe('function'))
    if (!resolveCreate) throw new Error('create promise not resolved')
    resolveCreate({ data: {} })
    await pending
    await flushPromises()
    expect(state.page.busyAction).toBe('')
    expect(state.page.isBusy).toBe(false)
  })

  it('surfaces a failed action through the located section error instead of a toast', async () => {
    const { renameDriveEntry } = await import('../../api/services/driveService')
    const renameDriveEntryMock = /** @type {import('vitest').Mock} */ (renameDriveEntry)
    renameDriveEntryMock.mockRejectedValueOnce(new Error('重命名被拒绝'))

    listDriveEntriesMock.mockResolvedValue({ data: [entry('file-1', 'a.txt')] })
    const state = mountState()
    await flushPromises()

    state.workspace.select(state.workspace.visibleEntries[0])
    state.workspace.renameDraft = 'b.txt'
    await state.entries.renameSelected()
    await flushPromises()

    expect(state.entries.renameError).toBe('重命名被拒绝')
    expect(state.page.error).toBe('')
    expect(state.page.busyAction).toBe('')
  })

  it('resets owner state and reloads for the new identity, discarding stale responses', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolvePrevious
    listDriveEntriesMock
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePrevious = resolve }))
      .mockResolvedValueOnce({ data: [entry('file-current', '当前身份文件')] })

    const state = mountState()
    await flushPromises()

    useAuthStore().installSession({
      accessToken: 'replacement-token',
      me: { userId: OTHER_USER_ID, username: 'other' }
    })
    await flushPromises()
    expect(listDriveEntries).toHaveBeenCalledTimes(2)
    expect(state.workspace.visibleEntries[0].entryId).toBe('file-current')
    if (!resolvePrevious) throw new Error('previous identity promise not resolved')
    resolvePrevious({ data: [entry('file-previous', '旧身份文件')] })
    await flushPromises()
    expect(state.workspace.visibleEntries[0].entryId).toBe('file-current')
    expect(state.page.error).toBe('')
  })

  it('clears the workspace when the session ends', async () => {
    listDriveEntriesMock.mockResolvedValue({ data: [entry('file-1', 'a.txt')] })
    const state = mountState()
    await flushPromises()
    expect(state.workspace.visibleEntries).toHaveLength(1)

    useAuthStore().clear()
    await flushPromises()

    expect(state.workspace.visibleEntries).toEqual([])
    expect(state.workspace.mode).toBe('files')
    expect(state.page.loading).toBe(false)
  })
})
