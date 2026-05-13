// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listActions, listReports, takeAction } = vi.hoisted(() => ({
  listActions: vi.fn(),
  listReports: vi.fn(),
  takeAction: vi.fn()
}))

vi.mock('../api/services/moderationService', () => ({
  listActions,
  listReports,
  takeAction
}))

import ModerationView from './ModerationView.vue'

function mountModerationView() {
  return mount(ModerationView, {
    global: {
      stubs: {
        UiBadge: { template: '<span><slot /></span>' },
        UiBreadcrumb: true,
        UiCard: { template: '<section><slot /></section>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiIconButton: {
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>'
        },
        UiPageHeader: { template: '<header><slot /><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiInput: {
          props: ['modelValue', 'disabled'],
          emits: ['update:modelValue'],
          template: '<input :disabled="disabled" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        UiSelect: {
          props: ['modelValue', 'disabled', 'options'],
          emits: ['update:modelValue'],
          template: '<select :disabled="disabled" :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option></select>'
        },
        UiTextarea: {
          props: ['modelValue', 'disabled'],
          emits: ['update:modelValue'],
          template: '<textarea :disabled="disabled" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        }
      }
    }
  })
}

describe('ModerationView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listActions.mockResolvedValue({ data: [], traceId: 'trace-actions' })
    listReports.mockResolvedValue({
      data: [
        {
          id: '22222222-2222-7222-8222-222222222222',
          reporterId: '11111111-1111-7111-8111-111111111111',
          targetType: 1,
          targetId: '33333333-3333-7333-8333-333333333333',
          reason: 'spam',
          detail: 'spam detail',
          status: 0,
          createTime: '2026-04-29T00:00:00Z'
        }
      ],
      traceId: 'trace-reports'
    })
    takeAction.mockResolvedValue({ data: '44444444-4444-7444-8444-444444444444', traceId: 'trace-action' })
  })

  it('submits selected report ids as opaque UUID strings', async () => {
    const wrapper = mountModerationView()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '处置').trigger('click')
    expect(wrapper.text()).toContain('风险动作')
    expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    await wrapper.find('textarea').setValue('confirmed spam')
    await wrapper.findAll('button').find((button) => button.text() === '确认处置').trigger('click')
    await flushPromises()

    expect(takeAction).toHaveBeenCalledWith({
      reportId: '22222222-2222-7222-8222-222222222222',
      action: 'reject',
      reason: 'confirmed spam',
      durationSeconds: undefined
    })
  })
})
