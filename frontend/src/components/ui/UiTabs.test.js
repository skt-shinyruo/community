// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import UiTabs from './UiTabs.vue'

const TABS = [
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '最热' },
  { value: 'following', label: '关注', disabled: true }
]

const wrappers = []

function track(wrapper) {
  wrappers.push(wrapper)
  return wrapper
}

function mountTabs(props = {}, options = {}) {
  return track(
    mount(UiTabs, {
      attachTo: document.body,
      ...options,
      props: { tabs: TABS, label: '帖子排序', modelValue: 'latest', ...props }
    })
  )
}

function tabsOf(wrapper) {
  return wrapper.findAll('[role="tab"]')
}

function panelsOf(wrapper) {
  return wrapper.findAll('[role="tabpanel"]')
}

afterEach(() => {
  while (wrappers.length) wrappers.pop().unmount()
})

describe('UiTabs', () => {
  it('exposes tablist/tab/tabpanel semantics with full tab-panel linkage', () => {
    const wrapper = mountTabs()

    expect(wrapper.get('[role="tablist"]').attributes('aria-label')).toBe('帖子排序')
    const tabs = tabsOf(wrapper)
    const panels = panelsOf(wrapper)
    expect(tabs).toHaveLength(3)
    expect(panels).toHaveLength(3)

    for (const [index, tab] of tabs.entries()) {
      expect(tab.attributes('aria-controls')).toBe(panels[index].attributes('id'))
      expect(panels[index].attributes('aria-labelledby')).toBe(tab.attributes('id'))
    }

    // 漫游 tabindex：只有选中 tab 在 Tab 序列里；只有激活面板可见
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[0].attributes('tabindex')).toBe('0')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    expect(tabs[1].attributes('tabindex')).toBe('-1')
    expect(panels[0].isVisible()).toBe(true)
    expect(panels[1].isVisible()).toBe(false)
    expect(panels[2].isVisible()).toBe(false)
  })

  it('emits update:modelValue on click without mutating the controlled selection itself', async () => {
    const wrapper = mountTabs()

    await tabsOf(wrapper)[1].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['hot']])
    // 受控形态：调用方回写 modelValue 之前选中态不自行变化
    expect(tabsOf(wrapper)[0].attributes('aria-selected')).toBe('true')
  })

  it('does not emit for the active tab or a disabled tab', async () => {
    const wrapper = mountTabs()
    const tabs = tabsOf(wrapper)
    expect(tabs[2].attributes('disabled')).toBeDefined()

    await tabs[0].trigger('click')
    await tabs[2].trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(tabs[0].attributes('aria-selected')).toBe('true')
  })

  it('cycles with arrow keys, wraps around, skips disabled tabs and moves focus', async () => {
    const wrapper = mountTabs({
      'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value })
    })
    const list = wrapper.get('[role="tablist"]')

    await list.trigger('keydown', { key: 'ArrowRight' })
    expect(tabsOf(wrapper)[1].attributes('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[1].element)

    // 从「最热」向右跳过禁用的「关注」并循环回「最新」
    await list.trigger('keydown', { key: 'ArrowRight' })
    expect(tabsOf(wrapper)[0].attributes('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[0].element)

    // 向左同样跳过禁用项并循环
    await list.trigger('keydown', { key: 'ArrowLeft' })
    expect(tabsOf(wrapper)[1].attributes('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[1].element)
  })

  it('jumps to the first and last enabled tabs with Home and End', async () => {
    const wrapper = mountTabs({
      'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value })
    })
    const list = wrapper.get('[role="tablist"]')

    // End 落到最后一个可用 tab（跳过禁用的「关注」）
    await list.trigger('keydown', { key: 'End' })
    expect(tabsOf(wrapper)[1].attributes('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[1].element)

    await list.trigger('keydown', { key: 'Home' })
    expect(tabsOf(wrapper)[0].attributes('aria-selected')).toBe('true')
    expect(document.activeElement).toBe(tabsOf(wrapper)[0].element)
  })

  it('falls back to the first enabled tab when modelValue is invalid or disabled without emitting', () => {
    const invalid = mountTabs({ modelValue: 'nope' })
    expect(tabsOf(invalid)[0].attributes('aria-selected')).toBe('true')
    expect(invalid.emitted('update:modelValue')).toBeUndefined()

    const disabledTarget = mountTabs({ modelValue: 'following' })
    expect(tabsOf(disabledTarget)[0].attributes('aria-selected')).toBe('true')
    expect(tabsOf(disabledTarget)[2].attributes('aria-selected')).toBe('false')
    expect(disabledTarget.emitted('update:modelValue')).toBeUndefined()
  })

  it('supports deep-link integration: callers map the selection to and from a query value', async () => {
    const navigations = []
    const wrapper = track(
      mount(
        {
          components: { UiTabs },
          data: () => ({ section: 'hot', tabs: TABS }),
          methods: {
            onSectionUpdate(value) {
              // 调用方在这里 router.replace({ query: { section: value } })
              navigations.push(value)
              this.section = value
            }
          },
          template: `
            <UiTabs
              :model-value="section"
              :tabs="tabs"
              label="设置分区"
              @update:model-value="onSectionUpdate"
            >
              <template #panel="{ tab, active }">
                <p v-if="active" class="section-body">{{ tab.value }} 内容</p>
              </template>
            </UiTabs>`
        },
        { attachTo: document.body }
      )
    )

    // 外部来源（路由 query）驱动选中态与面板内容
    expect(tabsOf(wrapper)[1].attributes('aria-selected')).toBe('true')
    expect(wrapper.get('.section-body').text()).toContain('hot')

    // 模拟路由变化：setData 等价于 query 更新后重新传入 modelValue
    await wrapper.setData({ section: 'latest' })
    expect(tabsOf(wrapper)[0].attributes('aria-selected')).toBe('true')
    expect(wrapper.get('.section-body').text()).toContain('latest')

    // 用户选择经事件交给调用方写回深链
    await tabsOf(wrapper)[1].trigger('click')
    expect(navigations).toEqual(['hot'])
    expect(tabsOf(wrapper)[1].attributes('aria-selected')).toBe('true')
  })

  it('renders custom tab labels and only mounts active panel content through slot props', () => {
    const wrapper = track(
      mount(
        {
          components: { UiTabs },
          data: () => ({ tabs: TABS, active: 'latest' }),
          template: `
            <UiTabs v-model="active" :tabs="tabs" label="帖子排序">
              <template #tab="{ tab }"><strong class="tab-label">{{ tab.label }}!</strong></template>
              <template #panel="{ tab, active: isActive }">
                <span v-if="isActive" class="panel-body">{{ tab.value }}</span>
              </template>
            </UiTabs>`
        },
        { attachTo: document.body }
      )
    )

    expect(wrapper.findAll('.tab-label')).toHaveLength(3)
    expect(wrapper.findAll('.tab-label')[0].text()).toBe('最新!')
    // 非激活面板存在但内容按 active 标志懒挂载
    expect(wrapper.findAll('.panel-body')).toHaveLength(1)
    expect(wrapper.get('.panel-body').text()).toBe('latest')
  })
})
