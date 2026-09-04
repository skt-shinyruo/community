// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import UiTooltip from './UiTooltip.vue'

function mockRects({ trigger, bubble } = {}) {
  const zero = { top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0 }
  return vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function rects() {
    if (this.classList?.contains('ui-tooltip__trigger')) return { ...zero, ...trigger }
    if (this.classList?.contains('ui-tooltip__bubble')) return { ...zero, ...bubble }
    return zero
  })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('UiTooltip', () => {
  it('shows on hover and links the bubble as the trigger description', async () => {
    const wrapper = mount(UiTooltip, {
      props: { text: '复制链接' },
      slots: { default: '<button type="button" aria-label="复制">复制</button>' }
    })
    const root = wrapper.get('.ui-tooltip')
    const triggerEl = wrapper.get('.ui-tooltip__trigger')
    expect(triggerEl.attributes('aria-describedby')).toBeUndefined()

    await root.trigger('mouseenter')
    await nextTick()

    const bubble = document.body.querySelector('[role="tooltip"]')
    expect(bubble).toBeTruthy()
    expect(bubble.textContent).toContain('复制链接')
    expect(triggerEl.attributes('aria-describedby')).toBe(bubble.id)

    await root.trigger('mouseleave')
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()
    expect(triggerEl.attributes('aria-describedby')).toBeUndefined()
  })

  it('shows on focus and hides on blur for keyboard users', async () => {
    const wrapper = mount(UiTooltip, {
      props: { text: '关注作者' },
      slots: { default: '<button type="button">关注</button>' }
    })
    const root = wrapper.get('.ui-tooltip')

    await root.trigger('focusin')
    await nextTick()
    expect(document.body.querySelector('[role="tooltip"]')).toBeTruthy()

    await root.trigger('focusout')
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()
  })

  it('closes on Escape', async () => {
    const wrapper = mount(UiTooltip, {
      props: { text: '提示' },
      slots: { default: '<button type="button">按钮</button>' }
    })
    const root = wrapper.get('.ui-tooltip')

    await root.trigger('mouseenter')
    await nextTick()
    expect(document.body.querySelector('[role="tooltip"]')).toBeTruthy()

    await root.trigger('keydown', { key: 'Escape' })
    expect(document.body.querySelector('[role="tooltip"]')).toBeNull()
  })

  it('prefers the content slot over the text prop', async () => {
    mount(UiTooltip, {
      props: { text: '兜底文案' },
      slots: {
        default: '<button type="button">按钮</button>',
        content: '<strong>格式化提示</strong>'
      }
    }).get('.ui-tooltip').trigger('mouseenter')
    await nextTick()
    await nextTick()

    const bubble = document.body.querySelector('[role="tooltip"]')
    expect(bubble.textContent).toContain('格式化提示')
    expect(bubble.textContent).not.toContain('兜底文案')
  })

  it('flips below the trigger when there is no room above', async () => {
    mockRects({
      trigger: { top: 4, left: 100, width: 40, height: 20, bottom: 24, right: 140 },
      bubble: { width: 120, height: 30, right: 120, bottom: 30 }
    })
    const wrapper = mount(UiTooltip, {
      props: { text: '提示' },
      slots: { default: '<button type="button">按钮</button>' }
    })

    await wrapper.get('.ui-tooltip').trigger('mouseenter')
    await nextTick()

    const bubble = document.body.querySelector('[role="tooltip"]')
    expect(bubble.classList.contains('ui-tooltip__bubble--bottom')).toBe(true)
    // top = triggerRect.bottom + 间距 = 24 + 6
    expect(bubble.style.top).toBe('30px')
    // 水平居中：100 + 40/2 - 120/2
    expect(bubble.style.left).toBe('60px')
  })

  it('clamps the bubble inside the viewport horizontally', async () => {
    mockRects({
      trigger: { top: 200, left: 0, width: 20, height: 20, bottom: 220, right: 20 },
      bubble: { width: 120, height: 30, right: 120, bottom: 30 }
    })
    const wrapper = mount(UiTooltip, {
      props: { text: '提示' },
      slots: { default: '<button type="button">按钮</button>' }
    })

    await wrapper.get('.ui-tooltip').trigger('mouseenter')
    await nextTick()

    const bubble = document.body.querySelector('[role="tooltip"]')
    expect(bubble.classList.contains('ui-tooltip__bubble--top')).toBe(true)
    expect(bubble.style.left).toBe('8px')
    expect(bubble.style.top).toBe('164px')
  })
})
