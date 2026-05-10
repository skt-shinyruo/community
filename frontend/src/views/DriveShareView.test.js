// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/driveService', () => ({
  getPublicDriveShare: vi.fn().mockResolvedValue({ data: { shareToken: 'token-a', name: 'a.txt', type: 'FILE' }, traceId: '' }),
  verifyDriveShare: vi.fn().mockResolvedValue({ data: { ticket: 'ticket-a' }, traceId: '' }),
  getDriveShareDownloadUrl: vi.fn().mockResolvedValue({ data: { url: 'https://cdn.example.test/file' }, traceId: '' })
}))

import DriveShareView from './DriveShareView.vue'

describe('DriveShareView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
  })
})
