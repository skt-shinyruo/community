// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import UiModalConfirm from './UiModalConfirm.vue'

describe('UiModalConfirm', () => {
  it('exposes dialog semantics with labelled title and described message', () => {
    const wrapper = mount(UiModalConfirm, {
      props: {
        title: '确认操作',
        message: '该操作可能影响线上性能',
        confirmText: '继续'
      }
    })

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-labelledby')).toBeTruthy()
    expect(dialog.attributes('aria-describedby')).toBeTruthy()
    expect(wrapper.get(`#${dialog.attributes('aria-labelledby')}`).text()).toBe('确认操作')
    expect(wrapper.get(`#${dialog.attributes('aria-describedby')}`).text()).toContain('影响线上性能')
  })

  it('keeps the existing confirm / cancel button semantics', async () => {
    const wrapper = mount(UiModalConfirm, {
      props: { confirmText: '删除', confirmVariant: 'danger' }
    })
    const buttons = wrapper.findAll('.ui-modal__footer button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toBe('取消')
    expect(buttons[1].text()).toBe('删除')
    expect(buttons[1].classes()).toContain('danger')

    await buttons[1].trigger('click')
    expect(wrapper.emitted('confirm')).toHaveLength(1)

    await buttons[0].trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('emits cancel when Escape is pressed', async () => {
    const wrapper = mount(UiModalConfirm)

    await wrapper.get('dialog').trigger('cancel')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('emits cancel on backdrop click and the header close button', async () => {
    const wrapper = mount(UiModalConfirm)

    await wrapper.get('dialog').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)

    await wrapper.get('.ui-modal__close').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(2)
  })

  it('disables actions and blocks dismissal while busy', async () => {
    const wrapper = mount(UiModalConfirm, { props: { busy: true } })

    await wrapper.get('dialog').trigger('cancel')
    await wrapper.get('dialog').trigger('click')
    expect(wrapper.emitted('cancel')).toBeUndefined()
    for (const button of wrapper.findAll('button')) {
      expect(button.attributes('disabled')).toBeDefined()
    }
  })

  it('supports keyboard flow: initial focus, focus trap and focus restore', async () => {
    const trigger = document.createElement('button')
    trigger.textContent = '删除帖子'
    document.body.appendChild(trigger)
    trigger.focus()

    const wrapper = mount(UiModalConfirm, {
      attachTo: document.body,
      props: { title: '确认删除', message: '删除后不可恢复' }
    })
    await nextTick()

    // 初始焦点进入弹窗（首个可操作控件为头部关闭按钮）
    const close = wrapper.get('.ui-modal__close').element
    expect(document.activeElement).toBe(close)

    // Tab 在最后一个按钮上回绕到首个控件，Shift+Tab 反向回绕
    const confirm = wrapper.findAll('.ui-modal__footer button')[1].element
    confirm.focus()
    await wrapper.get('dialog').trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(close)

    close.focus()
    await wrapper.get('dialog').trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(confirm)

    // 关闭后焦点恢复到触发控件
    wrapper.unmount()
    expect(document.activeElement).toBe(trigger)
  })
})
