// @vitest-environment jsdom

import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TopbarSearchBox from './TopbarSearchBox.vue'

function mountSearchBox(props = {}) {
  return mount(TopbarSearchBox, {
    attachTo: document.body,
    props: {
      modelValue: '',
      isMac: false,
      ...props
    }
  })
}

describe('TopbarSearchBox', () => {
  it('emits update:modelValue when the desktop search input changes', async () => {
    const wrapper = mountSearchBox()

    await wrapper.get('input[type="search"]').setValue('spring')

    expect(wrapper.emitted('update:modelValue')).toEqual([['spring']])
  })

  it('emits submit when Enter is pressed inside the search input', async () => {
    const wrapper = mountSearchBox({ modelValue: 'java' })

    await wrapper.get('input[type="search"]').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('submit')).toEqual([[]])
  })

  it('focuses the search input on Ctrl/Cmd+K', async () => {
    const wrapper = mountSearchBox()
    const input = wrapper.get('input[type="search"]')

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true, bubbles: true }))
    await nextTick()

    expect(document.activeElement).toBe(input.element)
  })
})
