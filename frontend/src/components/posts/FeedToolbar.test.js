// @vitest-environment jsdom

import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import FeedToolbar from './FeedToolbar.vue'
import UiSelect from '../ui/UiSelect.vue'

function mountToolbar(props = {}) {
  return mount(FeedToolbar, {
    attachTo: document.body,
    props: {
      boardId: '',
      disabled: false,
      categories: [{ id: 'board-1', name: 'Java' }],
      ...props
    }
  })
}

describe('FeedToolbar', () => {
  it('emits update:boardId from the board select', async () => {
    const wrapper = mountToolbar()

    await wrapper.getComponent(UiSelect).vm.$emit('update:modelValue', 'board-1')

    expect(wrapper.emitted('update:boardId')).toEqual([['board-1']])
  })

  it('only exposes refresh and clear actions in the simplified toolbar contract', async () => {
    const wrapper = mountToolbar({ showClear: true })

    await wrapper.get('button[title="清空筛选与排序"]').trigger('click')
    await nextTick()
    const buttons = wrapper.findAll('button')
    await buttons[buttons.length - 1].trigger('click')

    expect(wrapper.emitted('clear')).toHaveLength(1)
    expect(wrapper.emitted('refresh')).toHaveLength(1)
    expect(wrapper.emitted('update:order')).toBeUndefined()
    expect(wrapper.emitted('update:filter')).toBeUndefined()
    expect(wrapper.emitted('update:subscribed')).toBeUndefined()
    expect(wrapper.emitted('update:tag')).toBeUndefined()
  })
})
