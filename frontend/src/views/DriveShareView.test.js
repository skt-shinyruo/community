// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/driveService', () => ({
  getPublicDriveShare: vi.fn().mockResolvedValue({
    data: { shareToken: 'token-a', requiresPassword: true, entryName: 'a.txt', entryType: 'FILE' },
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
          UiButton: { props: ['disabled', 'variant'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
          UiInput: { props: ['modelValue', 'type'], emits: ['update:modelValue'], template: '<input :type="type" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
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
          UiButton: { props: ['disabled', 'variant', 'type'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
          UiInput: { props: ['modelValue', 'type'], emits: ['update:modelValue'], template: '<input :type="type" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
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
      data: [{ entryId: 'child-folder', parentId: 'folder-root', entryType: 'FOLDER', name: 'Nested', status: 'ACTIVE' }],
      traceId: ''
    })

    const wrapper = mount(DriveShareView, {
      props: { shareToken: 'token-a' },
      global: {
        stubs: {
          UiCard: { template: '<section><slot /></section>' },
          UiBreadcrumb: true,
          UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
          UiButton: { props: ['disabled', 'variant', 'type'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
          UiInput: { props: ['modelValue', 'type'], emits: ['update:modelValue'], template: '<input :type="type" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }
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
})
