// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiEmpty from './UiEmpty.vue'

describe('UiEmpty', () => {
  it('keeps existing title description and action slots visible through UiState', () => {
    const wrapper = mount(UiEmpty, {
      slots: {
        default: '暂无通知',
        description: '当前没有新的通知。',
        actions: '<button>刷新</button>'
      }
    })

    expect(wrapper.findComponent({ name: 'UiState' }).exists()).toBe(true)
    expect(wrapper.text()).toContain('暂无通知')
    expect(wrapper.text()).toContain('当前没有新的通知。')
    expect(wrapper.find('button').text()).toBe('刷新')
  })

  it('maps legacy error type to error state variant', () => {
    const wrapper = mount(UiEmpty, {
      props: { type: 'error' },
      slots: { default: '加载失败' }
    })

    expect(wrapper.find('.ui-state--error').exists()).toBe(true)
  })
})
