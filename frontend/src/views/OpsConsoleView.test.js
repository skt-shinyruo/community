// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { reindex } = vi.hoisted(() => ({
  reindex: vi.fn()
}))

vi.mock('../api/services/searchService', () => ({
  reindex
}))

import OpsConsoleView from './OpsConsoleView.vue'

function mountView() {
  const showToast = vi.fn()

  return mount(OpsConsoleView, {
    global: {
      provide: {
        showToast
      },
      stubs: {
        UiCard: { props: ['flat'], template: '<section><slot /></section>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot /></header>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiModalConfirm: {
          props: ['title', 'message', 'confirmText', 'confirmVariant'],
          emits: ['confirm', 'cancel'],
          template: '<div><h2>{{ title }}</h2><p>{{ message }}</p><button @click="$emit(\'cancel\')">取消</button><button @click="$emit(\'confirm\')">{{ confirmText }}</button></div>'
        }
      }
    }
  })
}

describe('OpsConsoleView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    reindex.mockResolvedValue({
      data: { indexedCount: 42, jobId: 'job-1' },
      traceId: 'trace-reindex'
    })
  })

  it('confirms and submits the reindex action', async () => {
    const wrapper = mountView()

    await wrapper.findAll('button').find((button) => button.text() === '重建索引').trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '继续').trigger('click')
    await flushPromises()

    expect(reindex).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('已处理 42 条')
    expect(wrapper.text()).toContain('jobId=job-1')
  })
})
