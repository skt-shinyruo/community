// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiToolbar from './UiToolbar.vue'

describe('UiToolbar', () => {
  it('renders leading, filters, and actions slots', () => {
    const wrapper = mount(UiToolbar, {
      slots: {
        leading: '<span data-test="leading">订单</span>',
        filters: '<input data-test="filter" />',
        actions: '<button data-test="action">导出</button>'
      }
    })

    expect(wrapper.classes()).toContain('ui-toolbar')
    expect(wrapper.find('[data-test="leading"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="filter"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="action"]').exists()).toBe(true)
  })
})
