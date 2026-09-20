// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import UiToast from './UiToast.vue'

function mountToast() {
  return mount(UiToast, { attachTo: document.body })
}

describe('UiToast', () => {
  it('announces messages through a polite live region', async () => {
    const wrapper = mountToast()

    const region = wrapper.get('.toast-container')
    expect(region.attributes('role')).toBe('status')
    expect(region.attributes('aria-live')).toBe('polite')

    wrapper.vm.show({ title: '已保存', text: '内容已保存', duration: 0 })
    await wrapper.vm.$nextTick()

    expect(region.text()).toContain('已保存')
    expect(region.text()).toContain('内容已保存')
  })

  it('exposes an accessible close button that removes only its message', async () => {
    const wrapper = mountToast()
    wrapper.vm.show({ title: '通知', text: '第一条', duration: 0 })
    wrapper.vm.show({ title: '通知', text: '第二条', duration: 0 })
    await wrapper.vm.$nextTick()

    const toasts = wrapper.findAll('.toast')
    expect(toasts).toHaveLength(2)

    const close = toasts[0].get('button[aria-label="关闭通知"]')
    expect(close.attributes('type')).toBe('button')

    await close.trigger('click')
    const remaining = wrapper.findAll('.toast')
    expect(remaining).toHaveLength(1)
    expect(remaining[0].text()).toContain('第二条')
  })

  it('keeps the action button behaviour and removes the toast on action', async () => {
    const wrapper = mountToast()
    const onAction = vi.fn()
    wrapper.vm.show({ title: '通知', text: '正文', duration: 0, actionText: '查看', onAction })
    await wrapper.vm.$nextTick()

    const action = wrapper.get('.toast button:not([aria-label="关闭通知"])')
    expect(action.attributes('type')).toBe('button')
    expect(action.text()).toBe('查看')

    await action.trigger('click')
    expect(onAction).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.toast').exists()).toBe(false)
  })
})
