// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/driveService', () => ({
  getPublicDriveShare: vi.fn().mockResolvedValue({
    data: { shareToken: 'token-a', requiresPassword: true },
    traceId: ''
  }),
  verifyDriveShare: vi.fn().mockResolvedValue({
    data: { shareToken: 'token-a', entryId: 'file-root', entryName: 'a.txt', entryType: 'FILE', ticket: 'ticket-a' },
    traceId: ''
  }),
  listDriveShareEntries: vi.fn().mockResolvedValue({ data: [], traceId: '' }),
  getDriveShareDownloadUrl: vi.fn().mockResolvedValue({ data: { url: 'https://cdn.example.test/file' }, traceId: '' })
}))

import {
  getDriveShareDownloadUrl,
  getPublicDriveShare,
  listDriveShareEntries,
  verifyDriveShare
} from '../api/services/driveService'
import DriveShareView from './DriveShareView.vue'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('DriveShareView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('open', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders extraction code form', async () => {
    const wrapper = mount(DriveShareView, {
      props: { shareToken: 'token-a' },
      global: {
        stubs: {
          UiCard: { template: '<section><slot /></section>' },
          UiBreadcrumb: true,
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('提取码')
    expect(wrapper.text()).toContain('访问分享')
    expect(wrapper.text()).not.toContain('a.txt')
    expect(wrapper.text()).not.toContain('文件分享')
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
  })

  it('lists folder share children after verification and downloads child files', async () => {
    getPublicDriveShare.mockResolvedValueOnce({
      data: { shareToken: 'token-a', requiresPassword: true },
      traceId: ''
    })
    verifyDriveShare.mockResolvedValueOnce({
      data: { shareToken: 'token-a', entryId: 'folder-root', entryName: 'Folder', entryType: 'FOLDER', ticket: 'ticket-a' },
      traceId: ''
    })
    listDriveShareEntries
      .mockResolvedValueOnce({
        data: [
          { entryId: 'child-folder', parentId: 'folder-root', type: 'FOLDER', name: 'Nested', status: 'ACTIVE' },
          { entryId: 'child-file', parentId: 'folder-root', type: 'FILE', name: 'a.txt', status: 'ACTIVE' }
        ],
        traceId: ''
      })
      .mockResolvedValueOnce({
        data: [{ entryId: 'nested-file', parentId: 'child-folder', type: 'FILE', name: 'nested.txt', status: 'ACTIVE' }],
        traceId: ''
      })
    getDriveShareDownloadUrl.mockResolvedValue({
      data: { entryId: 'child-file', url: 'https://cdn.example.test/file' },
      traceId: ''
    })

    const wrapper = mount(DriveShareView, {
      props: { shareToken: 'token-a' },
      global: {
        stubs: {
          UiCard: { template: '<section><slot /></section>' },
          UiBreadcrumb: true,
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant', 'type'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()

    await wrapper.find('input[type="password"]').setValue('1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(listDriveShareEntries).toHaveBeenCalledWith('token-a', 'ticket-a', '')
    expect(wrapper.text()).toContain('Nested')
    expect(wrapper.text()).toContain('a.txt')

    await wrapper.find('[data-test="share-entry-download"]').trigger('click')
    await flushPromises()

    expect(getDriveShareDownloadUrl).toHaveBeenCalledWith('token-a', 'ticket-a', 'child-file')

    await wrapper.find('[data-test="share-entry-open"]').trigger('click')
    await flushPromises()

    expect(listDriveShareEntries).toHaveBeenLastCalledWith('token-a', 'ticket-a', 'child-folder')
  })

  it('renders folder shares from the verified entryType field', async () => {
    getPublicDriveShare.mockResolvedValueOnce({
      data: { shareToken: 'token-a', requiresPassword: true },
      traceId: ''
    })
    verifyDriveShare.mockResolvedValueOnce({
      data: { shareToken: 'token-a', entryId: 'folder-root', entryName: 'Folder', entryType: 'FOLDER', ticket: 'ticket-a' },
      traceId: ''
    })
    listDriveShareEntries.mockResolvedValueOnce({
      data: [{ entryId: 'child-folder', parentId: 'folder-root', type: 'FOLDER', name: 'Nested', status: 'ACTIVE' }],
      traceId: ''
    })

    const wrapper = mount(DriveShareView, {
      props: { shareToken: 'token-a' },
      global: {
        stubs: {
          UiCard: { template: '<section><slot /></section>' },
          UiBreadcrumb: true,
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant', 'type'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('Folder')

    await wrapper.find('input[type="password"]').setValue('1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('文件夹分享')
    expect(wrapper.text()).toContain('文件夹')
    expect(wrapper.text()).toContain('Nested')
    expect(wrapper.text()).not.toContain('文件分享')
  })

  it('ignores verification from the previous share token', async () => {
    const previousVerification = deferred()
    getPublicDriveShare
      .mockResolvedValueOnce({ data: { shareToken: 'token-a', requiresPassword: true }, traceId: '' })
      .mockResolvedValueOnce({ data: { shareToken: 'token-b', requiresPassword: true }, traceId: '' })
    verifyDriveShare
      .mockImplementationOnce(() => previousVerification.promise)
      .mockResolvedValueOnce({
        data: { shareToken: 'token-b', entryId: 'file-b', entryName: 'current-share.txt', entryType: 'FILE', ticket: 'ticket-b' },
        traceId: ''
      })

    const wrapper = mount(DriveShareView, {
      props: { shareToken: 'token-a' },
      global: {
        stubs: {
          UiCard: { template: '<section><slot /></section>' },
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant', 'type'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()

    await wrapper.find('input[type="password"]').setValue('old-password')
    await wrapper.find('form').trigger('submit.prevent')
    await vi.waitFor(() => expect(verifyDriveShare).toHaveBeenCalledTimes(1))

    await wrapper.setProps({ shareToken: 'token-b' })
    await flushPromises()
    await wrapper.find('input[type="password"]').setValue('new-password')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('current-share.txt')

    previousVerification.resolve({
      data: { shareToken: 'token-a', entryId: 'file-a', entryName: 'previous-share.txt', entryType: 'FILE', ticket: 'ticket-a' },
      traceId: ''
    })
    await flushPromises()

    expect(wrapper.text()).toContain('current-share.txt')
    expect(wrapper.text()).not.toContain('previous-share.txt')
  })

  it('keeps the latest folder navigation result when requests finish out of order', async () => {
    verifyDriveShare.mockResolvedValueOnce({
      data: { shareToken: 'token-a', entryId: 'folder-root', entryName: 'Folder', entryType: 'FOLDER', ticket: 'ticket-a' },
      traceId: ''
    })
    const previousFolderLoad = deferred()
    listDriveShareEntries
      .mockResolvedValueOnce({
        data: [{ entryId: 'child-folder', parentId: 'folder-root', type: 'FOLDER', name: 'Nested', status: 'ACTIVE' }],
        traceId: ''
      })
      .mockImplementationOnce(() => previousFolderLoad.promise)
      .mockResolvedValueOnce({
        data: [{ entryId: 'root-current', parentId: 'folder-root', type: 'FILE', name: 'root-current.txt', status: 'ACTIVE' }],
        traceId: ''
      })

    const wrapper = mount(DriveShareView, {
      props: { shareToken: 'token-a' },
      global: {
        stubs: {
          UiCard: { template: '<section><slot /></section>' },
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant', 'type'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()
    await wrapper.find('input[type="password"]').setValue('1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    await wrapper.find('[data-test="share-entry-open"]').trigger('click')
    await vi.waitFor(() => expect(listDriveShareEntries).toHaveBeenCalledTimes(2))
    await wrapper.find('[data-test="share-breadcrumb-root"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('root-current.txt')

    previousFolderLoad.resolve({
      data: [{ entryId: 'child-old', parentId: 'child-folder', type: 'FILE', name: 'child-old.txt', status: 'ACTIVE' }],
      traceId: ''
    })
    await flushPromises()

    expect(wrapper.text()).toContain('root-current.txt')
    expect(wrapper.text()).not.toContain('child-old.txt')
  })

  it('does not open a download URL from the previous share token', async () => {
    getPublicDriveShare
      .mockResolvedValueOnce({ data: { shareToken: 'token-a', requiresPassword: true }, traceId: '' })
      .mockResolvedValueOnce({ data: { shareToken: 'token-b', requiresPassword: true }, traceId: '' })
    verifyDriveShare.mockResolvedValueOnce({
      data: { shareToken: 'token-a', entryId: 'file-a', entryName: 'private-a.txt', entryType: 'FILE', ticket: 'ticket-a' },
      traceId: ''
    })
    const previousDownload = deferred()
    getDriveShareDownloadUrl.mockImplementationOnce(() => previousDownload.promise)

    const wrapper = mount(DriveShareView, {
      props: { shareToken: 'token-a' },
      global: {
        stubs: {
          UiCard: { template: '<section><slot /></section>' },
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant', 'type'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })
    await flushPromises()
    await wrapper.find('input[type="password"]').setValue('1234')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '下载').trigger('click')
    await vi.waitFor(() => expect(getDriveShareDownloadUrl).toHaveBeenCalledTimes(1))
    await wrapper.setProps({ shareToken: 'token-b' })
    await flushPromises()

    previousDownload.resolve({ data: { url: 'https://cdn.example.test/private-a' }, traceId: '' })
    await flushPromises()

    expect(window.open).not.toHaveBeenCalled()
  })
})
