// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import UiScrollTop from './UiScrollTop.vue'

function setScrollY(value) {
  Object.defineProperty(window, 'scrollY', { value, configurable: true, writable: true })
}

function mountScrollTop() {
  return mount(UiScrollTop, {
    global: {
      stubs: {
        UiIconButton: {
          template: '<button class="scroll-top-stub" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('UiScrollTop', () => {
  afterEach(() => {
    setScrollY(0)
  })

  it('initializes visibility from the current scroll position on mount', async () => {
    setScrollY(600)
    const visible = mountScrollTop()
    await visible.vm.$nextTick()
    expect(visible.find('.scroll-top-stub').exists()).toBe(true)

    setScrollY(100)
    const hidden = mountScrollTop()
    await hidden.vm.$nextTick()
    expect(hidden.find('.scroll-top-stub').exists()).toBe(false)
  })

  it('follows scroll events after mount and scrolls back to top on click', async () => {
    const scrollTo = vi.fn()
    const originalScrollTo = window.scrollTo
    window.scrollTo = scrollTo
    try {
      setScrollY(0)
      const wrapper = mountScrollTop()
      expect(wrapper.find('.scroll-top-stub').exists()).toBe(false)

      setScrollY(500)
      window.dispatchEvent(new Event('scroll'))
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.scroll-top-stub').exists()).toBe(true)

      await wrapper.find('.scroll-top-stub').trigger('click')
      expect(scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' })

      setScrollY(0)
      window.dispatchEvent(new Event('scroll'))
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.scroll-top-stub').exists()).toBe(false)
    } finally {
      window.scrollTo = originalScrollTo
    }
  })
})
