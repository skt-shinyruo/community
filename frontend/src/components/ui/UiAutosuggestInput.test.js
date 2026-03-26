// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiAutosuggestInput from './UiAutosuggestInput.vue'

function mountInput(props = {}) {
  return mount(UiAutosuggestInput, {
    attachTo: document.body,
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
    const wrapper = mountInput()

    await wrapper.get('input').setValue('ja')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['ja'])
  })

  it('does not auto-commit when a suggestion is selected', async () => {
    const wrapper = mountInput()

    await wrapper.get('input').setValue('ja')
    await wrapper.get('[role="option"][data-value="java"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['java'])
    expect(wrapper.emitted('select')).toEqual([['java']])
    expect(wrapper.emitted('commit')).toBeFalsy()
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

  it('supports arrow-key suggestion movement', async () => {
    const wrapper = mountInput()

    await wrapper.get('input').trigger('focus')
    await wrapper.get('input').trigger('keydown', { key: 'ArrowDown' })

    expect(wrapper.get('[role="option"][data-value="java"]').classes()).toContain('is-active')
  })

  it('does not react when disabled', async () => {
    const wrapper = mountInput({ disabled: true })

    await wrapper.get('input').trigger('focus')
    await wrapper.get('input').setValue('java')
    await wrapper.get('input').trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    expect(wrapper.emitted('commit')).toBeFalsy()
  })
})
