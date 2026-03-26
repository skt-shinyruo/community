// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import UiIconButton from './UiIconButton.vue'

describe('UiIconButton', () => {
  it('exposes the aria-label attribute from the required prop', () => {
    const wrapper = mount(UiIconButton, {
      props: {
        ariaLabel: '关闭对话框'
      },
      slots: {
        default: '×'
      }
    })

    expect(wrapper.get('button').attributes('aria-label')).toBe('关闭对话框')
  })

  it('forwards the disabled state', () => {
    const wrapper = mount(UiIconButton, {
      props: {
        ariaLabel: '发送消息',
        disabled: true
      },
      slots: {
        default: '↑'
      }
    })

    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })

  it('remains keyboard-triggerable', async () => {
    const wrapper = mount(UiIconButton, {
      props: {
        ariaLabel: '展开侧边栏'
      },
      slots: {
        default: '≡'
      }
    })

    await wrapper.get('button').trigger('keydown.enter')
    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('warns when ariaLabel is missing', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})

    mount(UiIconButton, {
      slots: {
        default: '×'
      }
    })

    expect(warnSpy).toHaveBeenCalled()
    warnSpy.mockRestore()
  })
})
