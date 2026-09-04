// @vitest-environment jsdom

import { DOMWrapper, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import UiDropdown from './UiDropdown.vue'

const ITEMS = [
  { value: 'follow', label: '关注作者' },
  { value: 'report', label: '举报' },
  { value: 'block', label: '屏蔽', disabled: true },
  { value: 'delete', label: '删除', danger: true }
]

const wrappers = []

function track(wrapper) {
  wrappers.push(wrapper)
  return wrapper
}

function mountDropdown(props = {}, options = {}) {
  return track(
    mount(UiDropdown, {
      attachTo: document.body,
      ...options,
      props: { items: ITEMS, label: '更多', ...props }
    })
  )
}

function menuEl() {
  return document.body.querySelector('[role="menu"]')
}

function menuItems() {
  return [...document.body.querySelectorAll('[role="menuitem"]')]
}

function menuWrapper() {
  return new DOMWrapper(menuEl())
}

async function openByClick(wrapper) {
  await wrapper.get('.ui-dropdown__trigger').trigger('click')
  await nextTick()
}

function mockRects({ trigger, menu } = {}) {
  const zero = { top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0 }
  return vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function rects() {
    if (this.classList?.contains('ui-dropdown__trigger')) return { ...zero, ...trigger }
    if (this.classList?.contains('ui-dropdown__menu')) return { ...zero, ...menu }
    return zero
  })
}

afterEach(() => {
  while (wrappers.length) wrappers.pop().unmount()
  vi.restoreAllMocks()
})

describe('UiDropdown', () => {
  it('exposes menu button semantics and focuses the first enabled item on click open', async () => {
    const wrapper = mountDropdown()
    const trigger = wrapper.get('.ui-dropdown__trigger')

    expect(trigger.text()).toContain('更多')
    expect(trigger.attributes('aria-haspopup')).toBe('menu')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(trigger.attributes('aria-controls')).toBeUndefined()
    expect(menuEl()).toBeNull()

    await openByClick(wrapper)

    const menu = menuEl()
    expect(menu).toBeTruthy()
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(trigger.attributes('aria-controls')).toBe(menu.id)
    expect(menu.getAttribute('aria-labelledby')).toBe(trigger.attributes('id'))

    const items = menuItems()
    expect(items.map((item) => item.textContent)).toEqual(['关注作者', '举报', '屏蔽', '删除'])
    expect(items[2].getAttribute('aria-disabled')).toBe('true')
    expect(items.every((item) => item.getAttribute('tabindex') === '-1')).toBe(true)
    expect(document.activeElement).toBe(items[0])
  })

  it('closes on a second trigger click without moving focus', async () => {
    const wrapper = mountDropdown()
    const trigger = wrapper.get('.ui-dropdown__trigger')

    await openByClick(wrapper)
    expect(menuEl()).toBeTruthy()

    await trigger.trigger('click')
    expect(menuEl()).toBeNull()
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })

  it('opens with ArrowDown focusing the first and ArrowUp the last enabled item', async () => {
    const wrapper = mountDropdown()
    const trigger = wrapper.get('.ui-dropdown__trigger')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await nextTick()
    expect(menuEl()).toBeTruthy()
    expect(document.activeElement).toBe(menuItems()[0])

    await menuWrapper().trigger('keydown', { key: 'Escape' })
    expect(menuEl()).toBeNull()

    // ArrowUp 打开时聚焦最后一个可用项（跳过禁用的「屏蔽」）
    await trigger.trigger('keydown', { key: 'ArrowUp' })
    await nextTick()
    expect(document.activeElement).toBe(menuItems()[3])
  })

  it('opens with Enter and Space on the trigger', async () => {
    const wrapper = mountDropdown()
    const trigger = wrapper.get('.ui-dropdown__trigger')

    await trigger.trigger('keydown', { key: 'Enter' })
    await nextTick()
    expect(menuEl()).toBeTruthy()
    expect(document.activeElement).toBe(menuItems()[0])

    await menuWrapper().trigger('keydown', { key: 'Escape' })

    await trigger.trigger('keydown', { key: ' ' })
    await nextTick()
    expect(menuEl()).toBeTruthy()
    expect(document.activeElement).toBe(menuItems()[0])
  })

  it('cycles focus with arrows skipping disabled items, and jumps with Home and End', async () => {
    const wrapper = mountDropdown()
    await openByClick(wrapper)
    const menu = menuWrapper()

    await menu.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(menuItems()[1])

    // 跳过禁用的「屏蔽」
    await menu.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(menuItems()[3])

    // 循环回首项
    await menu.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(menuItems()[0])

    // 向上循环回末项
    await menu.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(menuItems()[3])

    await menu.trigger('keydown', { key: 'Home' })
    expect(document.activeElement).toBe(menuItems()[0])

    await menu.trigger('keydown', { key: 'End' })
    expect(document.activeElement).toBe(menuItems()[3])
  })

  it('activates the focused item with Enter or Space, emits select and returns focus to the trigger', async () => {
    const wrapper = mountDropdown()
    const trigger = wrapper.get('.ui-dropdown__trigger')
    await openByClick(wrapper)
    const menu = menuWrapper()

    await menu.trigger('keydown', { key: 'ArrowDown' })
    await menu.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('select')).toEqual([[expect.objectContaining({ value: 'report' })]])
    expect(menuEl()).toBeNull()
    expect(document.activeElement).toBe(trigger.element)

    await openByClick(wrapper)
    await menuWrapper().trigger('keydown', { key: ' ' })
    expect(wrapper.emitted('select')[1]).toEqual([expect.objectContaining({ value: 'follow' })])
    expect(document.activeElement).toBe(trigger.element)
  })

  it('activates items on click and returns focus to the trigger', async () => {
    const wrapper = mountDropdown()
    const trigger = wrapper.get('.ui-dropdown__trigger')
    await openByClick(wrapper)

    await new DOMWrapper(menuItems()[3]).trigger('click')

    expect(wrapper.emitted('select')).toEqual([[expect.objectContaining({ value: 'delete', danger: true })]])
    expect(menuEl()).toBeNull()
    expect(document.activeElement).toBe(trigger.element)
  })

  it('marks danger items with a dedicated class', async () => {
    const wrapper = mountDropdown()
    await openByClick(wrapper)

    expect(menuItems()[3].classList.contains('ui-dropdown__item--danger')).toBe(true)
    expect(menuItems()[0].classList.contains('ui-dropdown__item--danger')).toBe(false)
  })

  it('ignores clicks on disabled items and keeps the menu open', async () => {
    const wrapper = mountDropdown()
    await openByClick(wrapper)

    await new DOMWrapper(menuItems()[2]).trigger('click')

    expect(wrapper.emitted('select')).toBeUndefined()
    expect(menuEl()).toBeTruthy()
  })

  it('closes on Escape and returns focus to the trigger', async () => {
    const wrapper = mountDropdown()
    const trigger = wrapper.get('.ui-dropdown__trigger')
    await openByClick(wrapper)

    await menuWrapper().trigger('keydown', { key: 'Escape' })

    expect(menuEl()).toBeNull()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger.element)
  })

  it('closes on outside pointerdown but ignores interactions inside the trigger and menu', async () => {
    const wrapper = mountDropdown()
    await openByClick(wrapper)

    menuEl().dispatchEvent(new Event('pointerdown', { bubbles: true }))
    expect(menuEl()).toBeTruthy()

    wrapper.get('.ui-dropdown__trigger').element.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    expect(menuEl()).toBeTruthy()

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(menuEl()).toBeNull()
  })

  it('closes on Tab without stealing focus back', async () => {
    const wrapper = mountDropdown()
    await openByClick(wrapper)

    await menuWrapper().trigger('keydown', { key: 'Tab' })

    expect(menuEl()).toBeNull()
    expect(document.activeElement).not.toBe(wrapper.get('.ui-dropdown__trigger').element)
  })

  it('does not open when the trigger is disabled', async () => {
    const wrapper = mountDropdown({ disabled: true })
    const trigger = wrapper.get('.ui-dropdown__trigger')
    expect(trigger.attributes('disabled')).toBeDefined()

    await trigger.trigger('click')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await nextTick()

    expect(menuEl()).toBeNull()
  })

  it('stays safe with no enabled items: opens without focus change and closes on Escape', async () => {
    const wrapper = mountDropdown({ items: [{ value: 'block', label: '屏蔽', disabled: true }] })

    await openByClick(wrapper)
    expect(menuEl()).toBeTruthy()
    // 没有可聚焦项：焦点留在菜单外，不进入菜单
    expect(document.activeElement).toBe(document.body)

    await menuWrapper().trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(document.body)

    await menuWrapper().trigger('keydown', { key: 'Escape' })
    expect(menuEl()).toBeNull()
  })

  it('positions below the trigger and flips above when there is no room below', async () => {
    const zero = { top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0 }
    const spy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function rects() {
      if (this.classList?.contains('ui-dropdown__trigger')) {
        return { ...zero, top: 100, left: 50, width: 80, height: 32, bottom: 132, right: 130 }
      }
      if (this.classList?.contains('ui-dropdown__menu')) {
        return { ...zero, width: 120, height: 160, right: 120, bottom: 160 }
      }
      return zero
    })
    const wrapper = mountDropdown()
    await openByClick(wrapper)

    let menu = menuEl()
    expect(menu.classList.contains('ui-dropdown__menu--bottom')).toBe(true)
    // top = triggerRect.bottom + 间距 = 132 + 4
    expect(menu.style.top).toBe('136px')
    expect(menu.style.left).toBe('50px')

    await menuWrapper().trigger('keydown', { key: 'Escape' })

    // 触发点贴近视口底部（jsdom innerHeight 默认 768）：下方 36px 放不下 160px 菜单，翻到上方
    spy.mockImplementation(function rects() {
      if (this.classList?.contains('ui-dropdown__trigger')) {
        return { ...zero, top: 700, left: 50, width: 80, height: 32, bottom: 732, right: 130 }
      }
      if (this.classList?.contains('ui-dropdown__menu')) {
        return { ...zero, width: 120, height: 160, right: 120, bottom: 160 }
      }
      return zero
    })
    await openByClick(wrapper)

    menu = menuEl()
    expect(menu.classList.contains('ui-dropdown__menu--top')).toBe(true)
    // top = triggerRect.top - 菜单高度 - 间距 = 700 - 160 - 4
    expect(menu.style.top).toBe('536px')
  })

  it('clamps the menu inside the viewport horizontally', async () => {
    mockRects({
      trigger: { top: 100, left: 960, width: 60, height: 32, bottom: 132, right: 1020 },
      menu: { width: 160, height: 120, right: 160, bottom: 120 }
    })
    const wrapper = mountDropdown()
    await openByClick(wrapper)

    const menu = menuEl()
    // jsdom innerWidth 默认 1024：960 + 160 溢出，夹取到 1024 - 160 - 8
    expect(menu.style.left).toBe('856px')
    expect(menu.style.top).toBe('136px')
  })
})
