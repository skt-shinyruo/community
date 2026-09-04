// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiTextarea from './UiTextarea.vue'

describe('UiTextarea', () => {
  it('binds modelValue and emits typed values', async () => {
    const wrapper = mount(UiTextarea, { props: { modelValue: '第一行' } })
    const textarea = wrapper.get('textarea')

    expect(textarea.element.value).toBe('第一行')

    await textarea.setValue('第一行\n第二行')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['第一行\n第二行'])
  })

  it('honors the trim modifier', async () => {
    const wrapper = mount(UiTextarea, {
      props: { modelValue: '', modelModifiers: { trim: true } }
    })

    await wrapper.get('textarea').setValue('  内容  ')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['内容'])
  })

  it('passes native attributes through to the textarea element', () => {
    const wrapper = mount(UiTextarea, {
      attrs: {
        rows: '3',
        placeholder: '请输入内容',
        name: 'reason',
        maxlength: '200'
      }
    })
    const textarea = wrapper.get('textarea')

    expect(textarea.attributes('rows')).toBe('3')
    expect(textarea.attributes('placeholder')).toBe('请输入内容')
    expect(textarea.attributes('name')).toBe('reason')
    expect(textarea.attributes('maxlength')).toBe('200')
  })

  it('forwards the disabled state', () => {
    const wrapper = mount(UiTextarea, { attrs: { disabled: true } })

    expect(wrapper.get('textarea').attributes('disabled')).toBeDefined()
  })

  it('stays free of field wiring outside a UiField', () => {
    const wrapper = mount(UiTextarea)
    const textarea = wrapper.get('textarea')

    expect(textarea.attributes('id')).toBeUndefined()
    expect(textarea.attributes('aria-describedby')).toBeUndefined()
    expect(textarea.attributes('aria-invalid')).toBeUndefined()
  })
})
