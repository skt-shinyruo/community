// @vitest-environment jsdom

import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import FeedToolbar from './FeedToolbar.vue'
import UiDropdown from '../ui/UiDropdown.vue'

const wrappers = []

function mountToolbar(props = {}) {
  const wrapper = mount(FeedToolbar, {
    attachTo: document.body,
    props: {
      categoryId: '',
      tag: '',
      disabled: false,
      categories: [
        { id: 'category-1', name: '技术讨论' },
        { id: 'category-2', name: '面经' }
      ],
      ...props
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  while (wrappers.length) wrappers.pop().unmount()
})

describe('FeedToolbar', () => {
  it('emits update:categoryId from the category dropdown menu', async () => {
    const wrapper = mountToolbar()

    const dropdown = wrapper.getComponent(UiDropdown)
    await dropdown.get('button').trigger('click')
    await nextTick()

    const menuItems = document.body.querySelectorAll('[role="menuitem"]')
    expect(menuItems).toHaveLength(3)
    expect(menuItems[0].textContent).toBe('全部分类')
    menuItems[1].click()
    await nextTick()

    expect(wrapper.emitted('update:categoryId')).toEqual([['category-1']])
    // 选中后菜单关闭且焦点返回 trigger
    expect(document.body.querySelectorAll('[role="menuitem"]')).toHaveLength(0)
    expect(document.activeElement).toBe(dropdown.get('button').element)
  })

  it('shows the current category label and resets to all categories', async () => {
    const wrapper = mountToolbar({ categoryId: 'category-2' })

    expect(wrapper.get('.feed-toolbar-category-current').text()).toBe('面经')

    const dropdown = wrapper.getComponent(UiDropdown)
    await dropdown.get('button').trigger('click')
    await nextTick()
    document.body.querySelectorAll('[role="menuitem"]')[0].click()
    await nextTick()

    expect(wrapper.emitted('update:categoryId')).toEqual([['']])
  })

  it('renders a clearable chip for the active tag filter', async () => {
    const wrapper = mountToolbar({ tag: 'Java' })

    expect(wrapper.get('.feed-toolbar-tag-chip-text').text()).toBe('#Java')
    await wrapper.get('button[aria-label="清除标签 Java"]').trigger('click')

    expect(wrapper.emitted('clearTag')).toHaveLength(1)
  })

  it('exposes refresh and clear actions and respects the disabled state', async () => {
    const wrapper = mountToolbar({ showClear: true, tag: 'Java' })

    await wrapper.get('button[title="清空筛选与排序"]').trigger('click')
    await wrapper.get('.feed-toolbar-actions button.ghost').trigger('click')

    expect(wrapper.emitted('clear')).toHaveLength(1)
    expect(wrapper.emitted('refresh')).toHaveLength(1)

    const disabledWrapper = mountToolbar({ showClear: true, tag: 'Java', disabled: true })
    expect(disabledWrapper.get('button[title="清空筛选与排序"]').attributes('disabled')).toBeDefined()
    expect(disabledWrapper.get('button[aria-label="清除标签 Java"]').attributes('disabled')).toBeDefined()
    expect(disabledWrapper.getComponent(UiDropdown).props('disabled')).toBe(true)
  })

  it('hides the category dropdown when no categories are loaded', () => {
    const wrapper = mountToolbar({ categories: [] })

    expect(wrapper.findComponent(UiDropdown).exists()).toBe(false)
  })
})
