// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import UiModal from './UiModal.vue'

function mountModal(options = {}) {
  return mount(UiModal, {
    attachTo: document.body,
    ...options
  })
}

describe('UiModal', () => {
  it('exposes dialog semantics with title and body association', () => {
    const wrapper = mount(UiModal, {
      props: { title: '编辑资料' },
      slots: { default: '<p>正文内容</p>' }
    })

    const dialog = wrapper.get('dialog')
    expect(dialog.attributes('role')).toBe('dialog')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(wrapper.get(`#${dialog.attributes('aria-labelledby')}`).text()).toBe('编辑资料')
    expect(wrapper.get(`#${dialog.attributes('aria-describedby')}`).text()).toContain('正文内容')
  })

  it('supports size variants and header/body/footer slots', () => {
    const wrapper = mount(UiModal, {
      props: { title: '标题', size: 'lg' },
      slots: {
        default: '<p>主体</p>',
        footer: '<button type="button">保存</button>'
      }
    })

    expect(wrapper.get('dialog').classes()).toContain('ui-modal--lg')
    expect(wrapper.get('.ui-modal__header').text()).toContain('标题')
    expect(wrapper.get('.ui-modal__body').text()).toContain('主体')
    expect(wrapper.get('.ui-modal__footer').text()).toContain('保存')

    const customHeader = mount(UiModal, {
      slots: { header: '<span>自定义头部</span>' }
    })
    expect(customHeader.get('.ui-modal__header').text()).toContain('自定义头部')
    expect(customHeader.get('dialog').attributes('aria-labelledby')).toBeUndefined()
  })

  it('emits close on Escape, backdrop click and the close button', async () => {
    const wrapper = mount(UiModal, { props: { title: '标题' } })

    await wrapper.get('dialog').trigger('cancel')
    expect(wrapper.emitted('close')).toHaveLength(1)

    await wrapper.get('dialog').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(2)

    await wrapper.get('.ui-modal__card').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(2)

    await wrapper.get('.ui-modal__close').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(3)
  })

  it('suppresses dismissal while busy', async () => {
    const wrapper = mount(UiModal, {
      props: { title: '标题', busy: true }
    })

    await wrapper.get('dialog').trigger('cancel')
    await wrapper.get('dialog').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.get('.ui-modal__close').attributes('disabled')).toBeDefined()
    expect(wrapper.get('dialog').attributes('aria-busy')).toBe('true')
  })

  it('moves initial focus to the first operable control', async () => {
    mountModal({
      props: { title: '标题' },
      slots: { default: '<button type="button">主体按钮</button>' }
    })
    await nextTick()

    expect(document.activeElement?.getAttribute('aria-label')).toBe('关闭')
  })

  it('honours [data-autofocus] for the initial focus', async () => {
    mountModal({
      props: { title: '标题' },
      slots: {
        default: '<button type="button" data-autofocus>首选</button>',
        footer: '<button type="button">次要</button>'
      }
    })
    await nextTick()

    expect(document.activeElement?.textContent).toBe('首选')
  })

  it('traps Tab and Shift+Tab within the dialog', async () => {
    const wrapper = mountModal({
      props: { title: '标题' },
      slots: {
        default: '<button type="button">主体按钮</button>',
        footer: '<button type="button">确认</button>'
      }
    })
    await nextTick()
    const dialog = wrapper.get('dialog')
    const close = wrapper.get('.ui-modal__close').element
    const body = wrapper.get('.ui-modal__body button').element
    const confirm = wrapper.get('.ui-modal__footer button').element

    confirm.focus()
    await dialog.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(close)

    close.focus()
    await dialog.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(confirm)

    body.focus()
    await dialog.trigger('keydown', { key: 'Tab' })
    // 中间控件不拦截，交给浏览器默认 Tab 顺序（jsdom 中焦点保持不动）
    expect(document.activeElement).toBe(body)
  })

  it('restores focus to the trigger element after unmount', async () => {
    const trigger = document.createElement('button')
    trigger.textContent = '打开弹窗'
    document.body.appendChild(trigger)
    trigger.focus()

    const wrapper = mountModal({
      props: { title: '标题' },
      slots: { default: '<button type="button">主体按钮</button>' }
    })
    await nextTick()
    expect(document.activeElement).not.toBe(trigger)

    wrapper.unmount()
    expect(document.activeElement).toBe(trigger)
  })
})
