// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createDriveUploadSession, getDriveDownloadUrl, listDriveEntries, listDriveShares, uploadDriveFile } = vi.hoisted(() => ({
  createDriveUploadSession: vi.fn(),
  getDriveDownloadUrl: vi.fn(),
  listDriveEntries: vi.fn(),
  listDriveShares: vi.fn(),
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
  trashDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  restoreDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  deleteDriveEntryPermanently: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  getDriveDownloadUrl,
  createDriveShare: vi.fn().mockResolvedValue({ data: { shareToken: 'token-a', shareId: 'share-1', entryId: 'file-1', entryName: 'a.txt', entryType: 'FILE', expiresAt: '2026-05-10T00:00:00Z' }, traceId: '' }),
  revokeDriveShare: vi.fn().mockResolvedValue({ data: {}, traceId: '' })
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

function mountDrive(pinia) {
  return mount(DriveView, {
    global: {
      plugins: [pinia],
      stubs: {
        UiBreadcrumb: true,
        UiCard: { template: '<section><slot /></section>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
        UiIconButton: { props: ['ariaLabel'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
      }
    }
  })
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
    listDriveEntries.mockResolvedValue({ data: [], traceId: '' })
    listDriveShares.mockResolvedValue({ data: { items: [], hasNext: false, page: 0, size: 20 }, traceId: '' })
  })

  it('renders drive workspace actions', async () => {
    const wrapper = mount(DriveView, {
      global: {
        plugins: [pinia],
        stubs: {
          UiBreadcrumb: true,
          UiCard: { template: '<section><slot /></section>' },
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
          UiState: { template: '<div><slot /><slot name="description" /></div>' },
          UiInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
          UiIconButton: { props: ['ariaLabel'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('我的文件')
    expect(wrapper.text()).toContain('新建文件夹')
    expect(wrapper.text()).toContain('上传')
    expect(wrapper.text()).toContain('回收站')
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

    const wrapper = mount(DriveView, {
      global: {
        plugins: [pinia],
        stubs: {
          UiBreadcrumb: true,
          UiCard: { template: '<section><slot /></section>' },
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
          UiState: { template: '<div><slot /><slot name="description" /></div>' },
          UiInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
          UiIconButton: { props: ['ariaLabel'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
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
    const wrapper = mount(DriveView, {
      global: {
        plugins: [pinia],
        stubs: {
          UiBreadcrumb: true,
          UiCard: { template: '<section><slot /></section>' },
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
          UiState: { template: '<div><slot /><slot name="description" /></div>' },
          UiInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
          UiIconButton: { props: ['ariaLabel'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await vi.waitFor(() => expect(listDriveEntries).toHaveBeenCalledTimes(1))
    await vi.waitFor(() => {
      const shareButton = wrapper.findAll('button').find((button) => button.text() === '分享管理')
      expect(shareButton.attributes('disabled')).toBeUndefined()
    })

    await wrapper.findAll('button').find((button) => button.text() === '分享管理').trigger('click')
    await vi.waitFor(() => expect(listDriveShares).toHaveBeenCalledWith({ page: 0, size: 20 }))
    await vi.waitFor(() => expect(wrapper.text()).toContain('retained.txt'))
  })

  it('keeps file results visible when the share section fails', async () => {
    listDriveEntries.mockResolvedValue({
      data: [{ entryId: 'file-1', name: 'available.txt', type: 'FILE', status: 'ACTIVE' }],
      traceId: ''
    })
    listDriveShares.mockRejectedValueOnce(new Error('shares unavailable'))
    const wrapper = mountDrive(pinia)
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '分享管理').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('available.txt')
    expect(wrapper.text()).toContain('部分网盘数据加载失败：shares unavailable')
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

    const wrapper = mount(DriveView, {
      global: {
        plugins: [pinia],
        stubs: {
          UiBreadcrumb: true,
          UiCard: { template: '<section><slot /></section>' },
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
          UiState: { template: '<div><slot /><slot name="description" /></div>' },
          UiInput: { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
          UiIconButton: { props: ['ariaLabel'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '分享管理').trigger('click')
    await flushPromises()

    const loadMore = () => wrapper.findAll('button').find((button) => button.text() === '加载更多')
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

    auth.setAccessToken('access-rotated')
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

    await wrapper.findAll('button').find((button) => button.text() === '下载').trigger('click')
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
    const enterButton = wrapper.findAll('button').find((button) => button.text() === '进入')
    expect(enterButton.attributes('disabled')).toBeDefined()
    await enterButton.trigger('click')

    firstUpload.resolve({ data: {}, traceId: '' })
    await vi.waitFor(() => expect(createDriveUploadSession).toHaveBeenCalledTimes(2))
    await upload

    expect(createDriveUploadSession.mock.calls.map(([request]) => request.parentId)).toEqual(['', ''])
  })
})
