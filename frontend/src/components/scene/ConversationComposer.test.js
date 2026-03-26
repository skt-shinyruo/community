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
  it('emits submit when Enter is pressed and content is non-empty', async () => {
    const wrapper = mountComposer({ modelValue: 'hello' })

    await wrapper.get('textarea').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('submit')).toEqual([[]])
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
