// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiButton from './UiButton.vue'

function mountButton(props = {}, slots = {}) {
  return mount(UiButton, {
    props,
    slots: { default: '操作', ...slots },
    global: {
      stubs: {
        RouterLink: {
          name: 'RouterLink',
          props: ['to'],
          template: '<a :data-to="JSON.stringify(to)"><slot /></a>'
        }
      }
    }
  })
}

describe('UiButton', () => {
  it('renders a button by default with the shared variant classes', async () => {
    const wrapper = mountButton({ variant: 'secondary', title: '提示' })
    const button = wrapper.get('button')

    expect(button.text()).toBe('操作')
    expect(button.attributes('type')).toBe('button')
    expect(button.classes()).toContain('btn')
    expect(button.classes()).toContain('secondary')
    expect(button.attributes('title')).toBe('提示')

    await button.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('keeps the native submit type and disabled state on the button form', () => {
    const wrapper = mountButton({ type: 'submit', disabled: true })
    const button = wrapper.get('button')

    expect(button.attributes('type')).toBe('submit')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('renders a router link with the button appearance when to is given', async () => {
    const wrapper = mountButton({ variant: 'ghost', to: '/auth/password/reset' })
    const link = wrapper.get('a')

    expect(wrapper.find('button').exists()).toBe(false)
    expect(link.attributes('data-to')).toBe('"/auth/password/reset"')
    expect(link.classes()).toContain('btn')
    expect(link.classes()).toContain('ghost')
    expect(link.attributes('aria-disabled')).toBeUndefined()

    await link.trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('renders an anchor with the button appearance when href is given', () => {
    const wrapper = mountButton({ href: 'https://example.com/docs' })
    const link = wrapper.get('a')

    expect(link.attributes('href')).toBe('https://example.com/docs')
    expect(link.classes()).toContain('btn')
    expect(link.classes()).not.toContain('secondary')
  })

  it('blocks navigation clicks and marks the link disabled when disabled is set', async () => {
    const wrapper = mountButton({ to: '/posts', disabled: true })
    const link = wrapper.get('a')

    expect(link.attributes('aria-disabled')).toBe('true')
    await link.trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })
})
