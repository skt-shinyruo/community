// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/driveService', () => ({
  getDriveSpace: vi.fn().mockResolvedValue({ data: { quotaBytes: 10737418240, usedBytes: 0, remainingBytes: 10737418240 }, traceId: '' }),
  listDriveEntries: vi.fn().mockResolvedValue({ data: [], traceId: '' }),
  listDriveTrash: vi.fn().mockResolvedValue({ data: [], traceId: '' }),
  searchDriveEntries: vi.fn().mockResolvedValue({ data: [], traceId: '' }),
  createDriveFolder: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  createDriveUploadSession: vi.fn().mockResolvedValue({ data: { upload: { url: '/u', method: 'POST', fileField: 'file', fields: {} } }, traceId: '' }),
  uploadDriveFile: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  renameDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  moveDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  trashDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  restoreDriveEntry: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  deleteDriveEntryPermanently: vi.fn().mockResolvedValue({ data: {}, traceId: '' }),
  getDriveDownloadUrl: vi.fn().mockResolvedValue({ data: { url: 'https://cdn.example.test/file' }, traceId: '' }),
  createDriveShare: vi.fn().mockResolvedValue({ data: { shareToken: 'token-a', shareId: 'share-1', entryName: 'a.txt', expiresAt: '2026-05-10T00:00:00Z' }, traceId: '' }),
  revokeDriveShare: vi.fn().mockResolvedValue({ data: {}, traceId: '' })
}))

import DriveView from './DriveView.vue'

describe('DriveView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders drive workspace actions', async () => {
    const wrapper = mount(DriveView, {
      global: {
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
})
