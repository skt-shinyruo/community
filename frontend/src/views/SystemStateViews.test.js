// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'

import ForbiddenView from './ForbiddenView.vue'
import NotFoundView from './NotFoundView.vue'

function mountView(view) {
  return mount(view, {
    attachTo: document.body,
    global: {
      stubs: {
        RouterLink: {
          name: 'RouterLink',
          props: ['to'],
          template: '<a :data-to="JSON.stringify(to)" href="#"><slot /></a>'
        }
      }
    }
  })
}

describe('system state views', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it.each([
    ['403', ForbiddenView, '403 无权限', '你当前没有访问该页面的权限。最终结果仍以服务端鉴权为准。'],
    ['404', NotFoundView, '404 页面不存在', '当前地址没有对应内容。你可以返回讨论区，重新进入有效页面。']
  ])('%s renders an error UiState with a return path and moves focus to the region', (name, view, title, description) => {
    const wrapper = mountView(view)

    const region = wrapper.get('[role="status"]')
    expect(region.classes()).toContain('ui-state--error')
    expect(region.text()).toContain(title)
    expect(region.text()).toContain(description)

    const action = wrapper.get('.ui-state-actions a')
    expect(action.text()).toContain('返回帖子列表')
    expect(action.attributes('data-to')).toBe('"/posts"')
    expect(action.attributes('tabindex')).toBeUndefined()

    expect(document.activeElement).toBe(region.element)

    wrapper.unmount()
  })
})
