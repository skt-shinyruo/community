// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ConversationComposer from './ConversationComposer.vue'

function mountComposer(props = {}) {
  return mount(ConversationComposer, {
    props: {
      modelValue: '',
      disabled: false,
      ...props
    }
  })
}

describe('ConversationComposer', () => {
  it('submits once and prevents the default newline on a plain Enter', async () => {
    const wrapper = mountComposer({ modelValue: 'hello' })
    const textarea = wrapper.get('textarea')

    const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true })
    textarea.element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('submit')).toEqual([[]])
    expect(event.defaultPrevented).toBe(true)
  })

  it('does not emit submit or prevent the default while IME composition is active', async () => {
    const wrapper = mountComposer({ modelValue: '正在输入' })
    const textarea = wrapper.get('textarea')

    const event = new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, cancelable: true })
    textarea.element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('submit')).toBeFalsy()
    expect(event.defaultPrevented).toBe(false)
  })

  it('does not emit submit when the keydown only signals composition via keyCode 229', async () => {
    const wrapper = mountComposer({ modelValue: '正在输入' })
    const textarea = wrapper.get('textarea')

    const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true })
    Object.defineProperty(event, 'keyCode', { value: 229 })
    textarea.element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('submit')).toBeFalsy()
    expect(event.defaultPrevented).toBe(false)
  })

  it('does not emit submit when disabled', async () => {
    const wrapper = mountComposer({ modelValue: 'hello', disabled: true })

    await wrapper.get('textarea').trigger('keydown', { key: 'Enter' })
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')

    expect(wrapper.emitted('submit')).toBeFalsy()
    expect(wrapper.get('button[aria-label="发送消息"]').attributes('disabled')).toBeDefined()
  })

  it('emits submit when the send button is clicked', async () => {
    const wrapper = mountComposer({ modelValue: 'hello' })

    await wrapper.get('button[aria-label="发送消息"]').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[]])
  })
})
