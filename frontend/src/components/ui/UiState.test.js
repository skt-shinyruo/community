// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiState from './UiState.vue'

describe('UiState', () => {
  it('renders a compact empty state with title and description', () => {
    const wrapper = mount(UiState, {
      props: {
        variant: 'empty',
        title: '暂无订单',
        description: '当前筛选条件下没有订单。'
      }
    })

    expect(wrapper.classes()).toContain('ui-state')
    expect(wrapper.classes()).toContain('ui-state--empty')
    expect(wrapper.text()).toContain('暂无订单')
    expect(wrapper.text()).toContain('当前筛选条件下没有订单。')
  })

  it('renders error trace id and action slot', () => {
    const wrapper = mount(UiState, {
      props: {
        variant: 'error',
        title: '加载失败',
        traceId: 'trace-123'
      },
      slots: {
        actions: '<button>重试</button>'
      }
    })

    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).toContain('trace-123')
    expect(wrapper.find('button').text()).toBe('重试')
  })

  it('marks development-only state explicitly', () => {
    const wrapper = mount(UiState, {
      props: {
        variant: 'development',
        title: '开发辅助信息'
      }
    })

    expect(wrapper.attributes('data-development-only')).toBe('true')
    expect(wrapper.text()).toContain('Development only')
  })
})
