// @vitest-environment jsdom

import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import FeedToolbar from './FeedToolbar.vue'
import UiAutosuggestInput from '../ui/UiAutosuggestInput.vue'
import UiCheckbox from '../ui/UiCheckbox.vue'

function mountToolbar(props = {}) {
  return mount(FeedToolbar, {
    attachTo: document.body,
    props: {
      order: 'latest',
      filter: 'all',
      subscribed: false,
      showSubscribedToggle: true,
      disabled: false,
      categories: [],
      tagSuggestions: ['java', 'spring'],
      orderOptions: [
        { label: '最新', value: 'latest' },
        { label: '热门', value: 'hot' }
      ],
      filterOptions: [
        { label: '全部', value: 'all' },
        { label: '未读', value: 'unread' }
      ],
      ...props
    }
  })
}

describe('FeedToolbar', () => {
  it('emits update:subscribed from the shared checkbox toggle', async () => {
    const wrapper = mountToolbar()

    await wrapper.getComponent(UiCheckbox).vm.$emit('update:modelValue', true)

    expect(wrapper.emitted('update:subscribed')).toEqual([[true]])
  })

  it('emits update:tag when the shared autosuggest input commits a tag', async () => {
    const wrapper = mountToolbar()
    const tagInput = wrapper.getComponent(UiAutosuggestInput)

    await tagInput.vm.$emit('update:modelValue', 'java')
    await nextTick()
    await tagInput.vm.$emit('commit', 'java')

    expect(wrapper.emitted('update:tag')).toEqual([['java']])
  })

  it('keeps keyboard suggestion movement working through the shared autosuggest input', async () => {
    const wrapper = mountToolbar()
    const input = wrapper.getComponent(UiAutosuggestInput).get('input')

    await input.trigger('focus')
    await input.trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.get('[role="option"][data-value="java"]').classes()).toContain('is-active')
  })
})
