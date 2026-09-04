// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiInput from './UiInput.vue'

describe('UiInput', () => {
  it('binds modelValue and emits typed values', async () => {
    const wrapper = mount(UiInput, { props: { modelValue: '初始' } })
    const input = wrapper.get('input')

    expect(input.element.value).toBe('初始')

    await input.setValue('新值')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['新值'])
  })

  it('honors the trim modifier', async () => {
    const wrapper = mount(UiInput, {
      props: { modelValue: '', modelModifiers: { trim: true } }
    })

    await wrapper.get('input').setValue('  abc  ')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['abc'])
  })

  it('honors the number modifier with loose numeric conversion', async () => {
    const wrapper = mount(UiInput, {
      props: { modelValue: '', modelModifiers: { number: true } }
    })
    const input = wrapper.get('input')

    await input.setValue('42')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([42])

    await input.setValue('abc')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['abc'])
  })

  it('passes native attributes through to the input element', () => {
    const wrapper = mount(UiInput, {
      attrs: {
        type: 'password',
        placeholder: '请输入密码',
        autocomplete: 'current-password',
        name: 'password',
        minlength: '6',
        pattern: '^[a-z]+$'
      }
    })
    const input = wrapper.get('input')

    expect(input.attributes('type')).toBe('password')
    expect(input.attributes('placeholder')).toBe('请输入密码')
    expect(input.attributes('autocomplete')).toBe('current-password')
    expect(input.attributes('name')).toBe('password')
    expect(input.attributes('minlength')).toBe('6')
    expect(input.attributes('pattern')).toBe('^[a-z]+$')
  })

  it('forwards the disabled state', () => {
    const wrapper = mount(UiInput, { attrs: { disabled: true } })

    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
  })

  it('applies the sm size and ghost variant modifiers', () => {
    const wrapper = mount(UiInput, { props: { size: 'sm', variant: 'ghost' } })
    const input = wrapper.get('input')

    expect(input.classes()).toContain('ui-input--sm')
    expect(input.classes()).toContain('ui-input--ghost')
  })

  it('keeps the default md / outline appearance without modifier classes', () => {
    const wrapper = mount(UiInput)

    expect(wrapper.get('input').classes()).toEqual(['ui-input'])
  })

  it('stays free of field wiring outside a UiField', () => {
    const wrapper = mount(UiInput)
    const input = wrapper.get('input')

    expect(input.attributes('id')).toBeUndefined()
    expect(input.attributes('aria-describedby')).toBeUndefined()
    expect(input.attributes('aria-invalid')).toBeUndefined()
    expect(input.attributes('required')).toBeUndefined()
  })
})
