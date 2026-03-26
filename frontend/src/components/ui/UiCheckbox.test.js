// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiCheckbox from './UiCheckbox.vue'

describe('UiCheckbox', () => {
  it('emits boolean updates from the checkbox input', async () => {
    const wrapper = mount(UiCheckbox, {
      props: {
        modelValue: false,
        label: '仅看订阅'
      }
    })

    await wrapper.get('input[type="checkbox"]').setValue(true)

    expect(wrapper.emitted('update:modelValue')).toEqual([[true]])
  })

  it('respects the disabled state', () => {
    const wrapper = mount(UiCheckbox, {
      props: {
        modelValue: true,
        label: '已确认',
        disabled: true
      }
    })

    expect(wrapper.get('input[type="checkbox"]').attributes('disabled')).toBeDefined()
  })

  it('renders slot content over the label prop', () => {
    const wrapper = mount(UiCheckbox, {
      props: {
        modelValue: false,
        label: '默认标签'
      },
      slots: {
        default: '插槽标签'
      }
    })

    expect(wrapper.text()).toContain('插槽标签')
    expect(wrapper.text()).not.toContain('默认标签')
  })
})
