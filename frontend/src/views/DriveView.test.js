// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createDriveUploadSession, getDriveDownloadUrl, listDriveEntries, listDriveShares, showToast, trashDriveEntry, uploadDriveFile } = vi.hoisted(() => ({
  createDriveUploadSession: vi.fn(),
  getDriveDownloadUrl: vi.fn(),
  listDriveEntries: vi.fn(),
  listDriveShares: vi.fn(),
  showToast: vi.fn(),
  trashDriveEntry: vi.fn(),
  uploadDriveFile: vi.fn()
}))

vi.mock('../api/services/driveService', () => ({
  getDriveSpace: vi.fn().mockResolvedValue({ data: { quotaBytes: 10737418240, usedBytes: 0, remainingBytes: 10737418240 }, traceId: '' }),
  listDriveEntries,
  listDriveShares,
  listDriveTrash: vi.fn().mockResolvedValue({ data: [], traceId: '' }),
  searchDriveEntries: vi.fn().mockResolvedValue({ data: [], traceId: '' }),
  createDriveFolder: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  createDriveUploadSession,
  uploadDriveFile,
  renameDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  moveDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  trashDriveEntry,
  restoreDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  deleteDriveEntryPermanently: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  getDriveDownloadUrl,
  createDriveShare: vi.fn().mockResolvedValue({ data: { shareToken: 'token-a', shareId: 'share-1', entryId: 'file-1', entryName: 'a.txt', entryType: 'FILE', expiresAt: '2026-05-10T00:00:00Z' }, traceId: '' }),
  revokeDriveShare: vi.fn().mockResolvedValue({ data: {}, traceId: '' })
}))

vi.mock('../ui/toastService', () => ({
  showToast,
  showErrorToast: vi.fn(),
  setToastHandler: vi.fn()
}))

import { useAuthStore } from '../stores/auth'
import DriveView from './DriveView.vue'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const uiStubs = {
  UiCard: { template: '<section><slot /></section>' },
  UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
  UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>' },
  UiState: { props: ['variant', 'title'], template: '<div><slot /><slot name="description" /><slot name="actions" /></div>' },
  UiSkeleton: { props: ['variant', 'rows', 'label'], template: '<div class="skeleton-stub">{{ label }}</div>' },
  UiModalConfirm: {
    props: ['title', 'message', 'confirmText', 'confirmVariant'],
    emits: ['confirm', 'cancel'],
    template: '<div class="confirm-stub"><h2>{{ title }}</h2><p>{{ message }}</p><button @click="$emit(\'cancel\')">取消</button><button @click="$emit(\'confirm\')">{{ confirmText }}</button></div>'
  },
  UiBreadcrumb: {
    props: ['items', 'disabled'],
    emits: ['select'],
    template: '<nav><button v-for="(item, index) in items" :key="index" :disabled="disabled" @click="$emit(\'select\', index)">{{ item.label }}</button></nav>'
  }
}

function mountDrive(pinia) {
  return mount(DriveView, {
    global: {
      plugins: [pinia],
      stubs: uiStubs
    }
  })
}

function findButton(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

describe('DriveView', () => {
  let pinia
  let auth

  beforeEach(() => {
    vi.clearAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    auth = useAuthStore()
    auth.$patch({
      accessToken: 'access-a',
      me: { userId: 'user-a' },
      tokenGeneration: 1
    })
    getDriveDownloadUrl.mockResolvedValue({ data: { url: 'https://cdn.example.test/file' }, traceId: '' })
    createDriveUploadSession.mockResolvedValue({ data: { upload: { url: '/u', method: 'POST', fileField: 'file', fields: {} } }, traceId: '' })
    uploadDriveFile.mockResolvedValue({ data: {}, traceId: '' })
    trashDriveEntry.mockResolvedValue({ data: {}, traceId: '' })
    listDriveEntries.mockResolvedValue({ data: [], traceId: '' })
    listDriveShares.mockResolvedValue({ data: { items: [], hasNext: false, page: 0, size: 20 }, traceId: '' })
  })

  it('renders the drive workspace with tabs, toolbar and entry list', async () => {
    const wrapper = mountDrive(pinia)
    await flushPromises()

    expect(wrapper.text()).toContain('我的文件')
    expect(wrapper.text()).toContain('分享管理')
    expect(wrapper.text()).toContain('回收站')
    expect(wrapper.text()).toContain('新建文件夹')
    expect(wrapper.text()).toContain('上传')
    expect(wrapper.find('[role="tablist"]').exists()).toBe(true)
    expect(wrapper.findAll('[role="tab"]')).toHaveLength(3)
  })

  it('shows the first-load skeleton instead of bare loading text', async () => {
    const pending = deferred()
    listDriveEntries.mockImplementationOnce(() => pending.promise)
    const wrapper = mountDrive(pinia)
    await vi.waitFor(() => expect(listDriveEntries).toHaveBeenCalled())

    expect(wrapper.text()).toContain('加载网盘')

    pending.resolve({ data: [], traceId: '' })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无文件')
  })

  it('renders product drive labels instead of raw entry status', async () => {
    listDriveEntries.mockResolvedValue({
      data: [
        {
          entryId: 'file-1',
          name: 'guide.pdf',
          type: 'FILE',
          status: 'ACTIVE',
          sizeBytes: 1024,
          canShare: true
        }
      ],
      traceId: ''
    })

    const wrapper = mountDrive(pinia)
    await flushPromises()

    expect(wrapper.text()).toContain('可用')
    expect(wrapper.text()).toContain('可分享')
    expect(wrapper.text()).not.toContain('ACTIVE')
  })

  it('loads persisted shares when opening share management', async () => {
    listDriveShares.mockResolvedValue({
      data: {
        items: [{
          shareId: 'share-1',
          entryId: 'file-1',
          shareToken: 'token-a',
          entryName: 'retained.txt',
          entryType: 'FILE',
          expiresAt: '2026-05-10T00:00:00Z',
          status: 'ACTIVE'
        }],
        hasNext: false,
        page: 0,
        size: 20
      },
      traceId: ''
    })
    const wrapper = mountDrive(pinia)
    await vi.waitFor(() => expect(listDriveEntries).toHaveBeenCalledTimes(1))
    await flushPromises()

    await wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '分享管理').trigger('click')
    await vi.waitFor(() => expect(listDriveShares).toHaveBeenCalledWith({ page: 0, size: 20 }))
    await vi.waitFor(() => expect(wrapper.text()).toContain('retained.txt'))
  })

  it('switches workspace modes with the tablist arrow keys', async () => {
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await wrapper.get('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    await vi.waitFor(() => expect(findButton(wrapper, '刷新')).toBeTruthy())
    expect(listDriveShares).toHaveBeenCalledWith({ page: 0, size: 20 })
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('分享管理')

    await wrapper.get('[role="tablist"]').trigger('keydown', { key: 'End' })
    await vi.waitFor(() => expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('回收站'))
  })

  it('keeps file results available and reports the share section failure inline', async () => {
    listDriveEntries.mockResolvedValue({
      data: [{ entryId: 'file-1', name: 'available.txt', type: 'FILE', status: 'ACTIVE' }],
      traceId: ''
    })
    listDriveShares.mockRejectedValueOnce(new Error('shares unavailable'))
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '分享管理').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('部分网盘数据加载失败：shares unavailable')

    await wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '我的文件').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('available.txt')
  })

  it('keeps persisted shares and retries the same page after load-more fails', async () => {
    listDriveShares
      .mockResolvedValueOnce({
        data: {
          items: [{
            shareId: 'share-1',
            entryId: 'file-1',
            shareToken: 'token-a',
            entryName: 'first-share.txt',
            entryType: 'FILE',
            expiresAt: '2026-05-10T00:00:00Z',
            status: 'ACTIVE'
          }],
          hasNext: true,
          page: 0,
          size: 20
        },
        traceId: ''
      })
      .mockRejectedValueOnce(new Error('temporary share failure'))
      .mockResolvedValueOnce({
        data: {
          items: [{
            shareId: 'share-2',
            entryId: 'file-2',
            shareToken: 'token-b',
            entryName: 'second-share.txt',
            entryType: 'FILE',
            expiresAt: '2026-05-11T00:00:00Z',
            status: 'ACTIVE'
          }],
          hasNext: false,
          page: 1,
          size: 20
        },
        traceId: ''
      })

    const wrapper = mountDrive(pinia)
    await flushPromises()
    await wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '分享管理').trigger('click')
    await flushPromises()

    const loadMore = () => findButton(wrapper, '加载更多')
    await loadMore().trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('first-share.txt')
    expect(wrapper.text()).toContain('temporary share failure')

    await loadMore().trigger('click')
    await flushPromises()
    expect(listDriveShares.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.text()).toContain('first-share.txt')
    expect(wrapper.text()).toContain('second-share.txt')
  })

  it('creates a folder from the empty-state action and reports validation inline', async () => {
    const { createDriveFolder } = await import('../api/services/driveService')
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await wrapper.get('.drive-entry-section').findAll('button').find((button) => button.text() === '新建文件夹').trigger('click')
    const folderInput = wrapper.get('input[placeholder="输入文件夹名称"]')
    await folderInput.setValue('   ')
    await findButton(wrapper, '确认').trigger('click')
    expect(wrapper.text()).toContain('请输入文件夹名称')
    expect(createDriveFolder).not.toHaveBeenCalled()

    await folderInput.setValue('资料')
    await findButton(wrapper, '确认').trigger('click')
    await flushPromises()
    expect(createDriveFolder).toHaveBeenCalledWith({ parentId: '', name: '资料' })
    expect(showToast).not.toHaveBeenCalled()
  })

  it('confirms trash with UiModalConfirm before deleting', async () => {
    listDriveEntries.mockResolvedValue({
      data: [{ entryId: 'file-1', name: 'active.txt', type: 'FILE', status: 'ACTIVE', canShare: true }],
      traceId: ''
    })
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await wrapper.get('.drive-entry-row').trigger('click')
    await findButton(wrapper, '删除').trigger('click')

    const dialog = wrapper.get('.confirm-stub')
    expect(dialog.text()).toContain('删除到回收站')
    expect(dialog.text()).toContain('active.txt')
    expect(trashDriveEntry).not.toHaveBeenCalled()

    await findButton(dialog, '移至回收站').trigger('click')
    await flushPromises()
    expect(trashDriveEntry).toHaveBeenCalledWith('file-1')
    expect(wrapper.find('.confirm-stub').exists()).toBe(false)
  })

  it('cancels the trash confirmation without calling the API', async () => {
    listDriveEntries.mockResolvedValue({
      data: [{ entryId: 'file-1', name: 'active.txt', type: 'FILE', status: 'ACTIVE' }],
      traceId: ''
    })
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await wrapper.get('.drive-entry-row').trigger('click')
    await findButton(wrapper, '删除').trigger('click')
    await findButton(wrapper.get('.confirm-stub'), '取消').trigger('click')

    expect(wrapper.find('.confirm-stub').exists()).toBe(false)
    expect(trashDriveEntry).not.toHaveBeenCalled()
  })

  it('confirms permanent delete from the trash workspace', async () => {
    const { listDriveTrash, deleteDriveEntryPermanently } = await import('../api/services/driveService')
    listDriveTrash.mockResolvedValue({
      data: [{ entryId: 'file-9', name: 'old.txt', type: 'FILE', status: 'TRASHED' }],
      traceId: ''
    })
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '回收站').trigger('click')
    await flushPromises()
    await wrapper.get('.drive-entry-row').trigger('click')

    await findButton(wrapper, '彻底删除').trigger('click')
    const dialog = wrapper.get('.confirm-stub')
    expect(dialog.text()).toContain('彻底删除')
    expect(dialog.text()).toContain('无法恢复')
    expect(deleteDriveEntryPermanently).not.toHaveBeenCalled()

    await findButton(dialog, '彻底删除').trigger('click')
    await flushPromises()
    expect(deleteDriveEntryPermanently).toHaveBeenCalledWith('file-9')
  })

  it('confirms share revoke because it affects existing link holders', async () => {
    const { revokeDriveShare } = await import('../api/services/driveService')
    listDriveShares.mockResolvedValue({
      data: {
        items: [{
          shareId: 'share-1',
          entryId: 'file-1',
          shareToken: 'token-a',
          entryName: 'shared.txt',
          entryType: 'FILE',
          expiresAt: '2026-05-10T00:00:00Z',
          status: 'ACTIVE'
        }],
        hasNext: false,
        page: 0,
        size: 20
      },
      traceId: ''
    })
    const wrapper = mountDrive(pinia)
    await flushPromises()
    await wrapper.findAll('[role="tab"]').find((tab) => tab.text() === '分享管理').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('shared.txt'))
    await vi.waitFor(() => expect(findButton(wrapper, '撤销').attributes('disabled')).toBeUndefined())

    await findButton(wrapper, '撤销').trigger('click')
    const dialog = wrapper.get('.confirm-stub')
    expect(dialog.text()).toContain('撤销分享')
    expect(revokeDriveShare).not.toHaveBeenCalled()

    await findButton(dialog, '撤销').trigger('click')
    await flushPromises()
    expect(revokeDriveShare).toHaveBeenCalledWith('share-1')
  })

  it('navigates folders through the path breadcrumb', async () => {
    listDriveEntries.mockResolvedValue({
      data: [{ entryId: 'folder-a', name: 'Folder A', type: 'FOLDER', status: 'ACTIVE' }],
      traceId: ''
    })
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await findButton(wrapper, '进入').trigger('click')
    await flushPromises()
    expect(listDriveEntries).toHaveBeenLastCalledWith({ parentId: 'folder-a' })

    const crumbs = wrapper.get('.drive-path nav').findAll('button')
    expect(crumbs.map((crumb) => crumb.text())).toEqual(['我的文件', 'Folder A'])
    await crumbs[0].trigger('click')
    await flushPromises()
    expect(listDriveEntries).toHaveBeenLastCalledWith({ parentId: '' })
  })

  it('does not let a previous auth generation overwrite the refreshed drive', async () => {
    const previousGenerationLoad = deferred()
    listDriveEntries
      .mockImplementationOnce(() => previousGenerationLoad.promise)
      .mockResolvedValueOnce({
        data: [{ entryId: 'file-current', name: 'current-generation.txt', type: 'FILE', status: 'ACTIVE' }],
        traceId: ''
      })

    const wrapper = mountDrive(pinia)
    await vi.waitFor(() => expect(listDriveEntries).toHaveBeenCalledTimes(1))

    auth.installSession({ accessToken: 'access-rotated' })
    await flushPromises()

    expect(wrapper.text()).toContain('current-generation.txt')

    previousGenerationLoad.resolve({
      data: [{ entryId: 'file-old', name: 'previous-generation.txt', type: 'FILE', status: 'ACTIVE' }],
      traceId: ''
    })
    await flushPromises()

    expect(wrapper.text()).toContain('current-generation.txt')
    expect(wrapper.text()).not.toContain('previous-generation.txt')
  })

  it('does not open a previous user download after the identity changes', async () => {
    listDriveEntries
      .mockResolvedValueOnce({
        data: [{ entryId: 'file-a', name: 'private-a.txt', type: 'FILE', status: 'ACTIVE' }],
        traceId: ''
      })
      .mockResolvedValueOnce({ data: [], traceId: '' })
    const previousDownload = deferred()
    getDriveDownloadUrl.mockImplementationOnce(() => previousDownload.promise)
    const open = vi.spyOn(window, 'open').mockImplementation(() => null)
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await findButton(wrapper, '下载').trigger('click')
    await vi.waitFor(() => expect(getDriveDownloadUrl).toHaveBeenCalledWith('file-a'))

    auth.installSession({
      accessToken: 'access-b',
      me: { userId: 'user-b' }
    })
    await flushPromises()

    previousDownload.resolve({ data: { url: 'https://cdn.example.test/private-a' }, traceId: '' })
    await flushPromises()

    expect(open).not.toHaveBeenCalled()
    open.mockRestore()
  })

  it('keeps every file in a multi-file upload bound to the starting folder', async () => {
    listDriveEntries.mockResolvedValue({
      data: [{ entryId: 'folder-a', name: 'Folder A', type: 'FOLDER', status: 'ACTIVE' }],
      traceId: ''
    })
    const firstUpload = deferred()
    uploadDriveFile
      .mockReturnValueOnce(firstUpload.promise)
      .mockResolvedValueOnce({ data: {}, traceId: '' })
    const wrapper = mountDrive(pinia)
    await flushPromises()
    const fileInput = wrapper.get('input[type="file"]')
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [
        new File(['one'], 'one.txt', { type: 'text/plain' }),
        new File(['two'], 'two.txt', { type: 'text/plain' })
      ]
    })

    const upload = fileInput.trigger('change')
    await vi.waitFor(() => expect(uploadDriveFile).toHaveBeenCalledTimes(1))
    const enterButton = findButton(wrapper, '进入')
    expect(enterButton.attributes('disabled')).toBeDefined()
    await enterButton.trigger('click')

    firstUpload.resolve({ data: {}, traceId: '' })
    await vi.waitFor(() => expect(createDriveUploadSession).toHaveBeenCalledTimes(2))
    await upload

    expect(createDriveUploadSession.mock.calls.map(([request]) => request.parentId)).toEqual(['', ''])
  })

  it('surfaces upload progress, cancellation and completion through the toast channel', async () => {
    const pendingUpload = deferred()
    uploadDriveFile.mockImplementationOnce(() => pendingUpload.promise)
    const wrapper = mountDrive(pinia)
    await flushPromises()

    const fileInput = wrapper.get('input[type="file"]')
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [new File(['one'], 'one.txt', { type: 'text/plain' })]
    })
    const firstRun = fileInput.trigger('change')
    await vi.waitFor(() => expect(uploadDriveFile).toHaveBeenCalledTimes(1))
    expect(findButton(wrapper, '取消上传')).toBeTruthy()

    await findButton(wrapper, '取消上传').trigger('click')
    expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: 'info', text: '上传已取消' }))
    pendingUpload.resolve({ data: {}, traceId: '' })
    await firstRun

    uploadDriveFile.mockResolvedValueOnce({ data: {}, traceId: '' })
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [new File(['two'], 'two.txt', { type: 'text/plain' })]
    })
    await fileInput.trigger('change')
    await flushPromises()
    expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: 'success', text: '已上传 1 个文件' }))
  })
})
