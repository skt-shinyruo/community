// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import UiModalConfirm from './UiModalConfirm.vue'

describe('UiModalConfirm', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })
  it('exposes dialog semantics with labelled title and description', () => {
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

  it('emits cancel when Escape is pressed', async () => {
    const wrapper = mount(UiModalConfirm)

    await wrapper.get('.modal-mask').trigger('keydown', { key: 'Escape' })

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('traps focus and restores the trigger after closing', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    const wrapper = mount(UiModalConfirm, { attachTo: document.body })
    await wrapper.vm.$nextTick()

    const buttons = wrapper.findAll('button')
    expect(document.activeElement).toBe(buttons[0].element)

    buttons.at(-1).element.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }))
    expect(document.activeElement).toBe(buttons[0].element)

    wrapper.unmount()
    expect(document.activeElement).toBe(trigger)
  })
})
