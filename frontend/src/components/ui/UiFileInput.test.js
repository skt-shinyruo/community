// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiFileInput from './UiFileInput.vue'

function createFile(name = 'avatar.png', type = 'image/png') {
  return new File(['avatar'], name, { type })
}

function mountInput(props = {}) {
  return mount(UiFileInput, {
    props: {
      modelValue: null,
      buttonText: '选择文件',
      clearable: true,
      ...props
    }
  })
}

async function selectFile(wrapper, file) {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: file ? [file] : []
  })
  await input.trigger('change')
}

describe('UiFileInput', () => {
  it('emits update:modelValue(file) when a file is selected', async () => {
    const wrapper = mountInput()
    const file = createFile()

    await selectFile(wrapper, file)

    expect(wrapper.emitted('update:modelValue')).toEqual([[file]])
  })

  it('emits update:modelValue(null) when the current file is cleared', async () => {
    const file = createFile()
    const wrapper = mountInput({ modelValue: file })

    await wrapper.get('.ui-file-input-clear').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
  })

  it('respects the disabled state', () => {
    const wrapper = mountInput({ disabled: true })

    expect(wrapper.get('input[type="file"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.ui-file-input-trigger').attributes('disabled')).toBeDefined()
  })

  it('shows visible file-name feedback for the selected file', () => {
    const file = createFile('picked-avatar.png')
    const wrapper = mountInput({ modelValue: file })

    expect(wrapper.text()).toContain('picked-avatar.png')
  })
})
