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
})
