// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => ({ name: 'postDetail', params: { postId: 'post-1' } })
  }
})

import UiBreadcrumb from './UiBreadcrumb.vue'

const routerLinkStub = {
  name: 'RouterLink',
  props: ['to'],
  template: '<a :data-to="JSON.stringify(to)"><slot /></a>'
}

function mountBreadcrumb(props = {}) {
  return mount(UiBreadcrumb, {
    props,
    global: { stubs: { RouterLink: routerLinkStub } }
  })
}

describe('UiBreadcrumb', () => {
  it('keeps the route-driven home plus registered items by default', () => {
    const wrapper = mountBreadcrumb()

    const links = wrapper.findAll('a')
    expect(links[0].text()).toBe('首页')
    expect(links[0].attributes('data-to')).toBe('"/"')
    expect(links[1].text()).toBe('帖子')
    expect(wrapper.text()).toContain('帖子 #post-1')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('renders controlled items for state-driven paths and emits select for ancestors', async () => {
    const onSelect = vi.fn()
    const wrapper = mount(UiBreadcrumb, {
      props: {
        items: [{ label: '我的文件' }, { label: '资料' }, { label: '2026' }],
        onSelect
      },
      global: { stubs: { RouterLink: routerLinkStub } }
    })

    expect(wrapper.text()).not.toContain('首页')
    const buttons = wrapper.findAll('button')
    expect(buttons.map((button) => button.text())).toEqual(['我的文件', '资料'])
    const current = wrapper.get('[aria-current="page"]')
    expect(current.text()).toBe('2026')
    expect(current.element.tagName).toBe('SPAN')

    await buttons[1].trigger('click')
    expect(onSelect).toHaveBeenCalledWith(1)
  })

  it('disables controlled ancestors while the workspace is busy', () => {
    const wrapper = mountBreadcrumb({
      items: [{ label: '我的文件' }, { label: '资料' }],
      disabled: true
    })

    for (const button of wrapper.findAll('button')) {
      expect(button.attributes('disabled')).toBeDefined()
    }
  })
})
