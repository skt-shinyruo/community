// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiAutosuggestInput from './UiAutosuggestInput.vue'

function mountInput(props = {}) {
  return mount(UiAutosuggestInput, {
    props: {
      modelValue: '',
      suggestions: ['java', 'spring'],
      commitOnEnter: true,
      commitOnBlur: true,
      ...props
    }
  })
}

describe('UiAutosuggestInput', () => {
  it('emits update:modelValue on typing', async () => {
    const wrapper = mountInput({ modelModifiers: { trim: true } })

    await wrapper.get('input').setValue('  ja  ')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['ja'])
  })

  it('uses a native datalist for suggestions', () => {
    const wrapper = mountInput()
    const input = wrapper.get('input')
    const datalist = wrapper.get('datalist')

    expect(input.attributes('list')).toBe(datalist.attributes('id'))
    expect(wrapper.findAll('option').map((option) => option.attributes('value'))).toEqual(['java', 'spring'])
  })

  it('commits on Enter when enabled', async () => {
    const wrapper = mountInput({ modelValue: 'java' })

    await wrapper.get('input').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('commit')).toEqual([['java']])
  })

  it('commits on blur when enabled', async () => {
    const wrapper = mountInput({ modelValue: 'spring' })

    await wrapper.get('input').trigger('blur')

    expect(wrapper.emitted('commit')).toEqual([['spring']])
  })

  it('does not react when disabled', async () => {
    const wrapper = mountInput({ disabled: true })

    await wrapper.get('input').setValue('java')
    await wrapper.get('input').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    expect(wrapper.emitted('commit')).toBeFalsy()
  })
})
