// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiField from './UiField.vue'
import UiInput from './UiInput.vue'
import UiTextarea from './UiTextarea.vue'

function mountField({ fieldProps = { label: '用户名' }, inner = '<UiInput />' } = {}) {
  return mount({
    components: { UiField, UiInput, UiTextarea },
    data: () => ({ fp: fieldProps }),
    template: `<UiField v-bind="fp">${inner}</UiField>`
  })
}

describe('UiField', () => {
  it('associates the label with the control so it becomes the accessible name', () => {
    const wrapper = mountField()
    const label = wrapper.get('label')
    const input = wrapper.get('input')

    expect(label.text()).toContain('用户名')
    expect(input.attributes('id')).toBeTruthy()
    expect(label.attributes('for')).toBe(input.attributes('id'))
    expect(input.attributes('aria-describedby')).toBeUndefined()
    expect(input.attributes('aria-invalid')).toBeUndefined()
  })

  it('links the help text through aria-describedby', () => {
    const wrapper = mountField({ fieldProps: { label: '用户名', help: '6-20 位字符' } })
    const help = wrapper.get('.ui-field-help')
    const input = wrapper.get('input')

    expect(help.text()).toBe('6-20 位字符')
    expect(input.attributes('aria-describedby')).toBe(help.attributes('id'))
    expect(input.attributes('aria-invalid')).toBeUndefined()
  })

  it('links the error text and marks the control invalid', () => {
    const wrapper = mountField({
      fieldProps: { label: '用户名', help: '6-20 位字符', error: '用户名已存在' }
    })
    const help = wrapper.get('.ui-field-help')
    const error = wrapper.get('.ui-field-error')
    const input = wrapper.get('input')

    expect(error.text()).toBe('用户名已存在')
    expect(error.attributes('role')).toBe('alert')
    expect(input.attributes('aria-describedby')?.split(' ')).toEqual([
      help.attributes('id'),
      error.attributes('id')
    ])
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('marks the control invalid through the invalid prop without an error message', () => {
    const wrapper = mountField({ fieldProps: { label: '用户名', invalid: true } })

    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
    expect(wrapper.find('.ui-field-error').exists()).toBe(false)
  })

  it('propagates required to the control with an aria-hidden marker and native validity', () => {
    const wrapper = mountField({ fieldProps: { label: '用户名', required: true } })
    const input = wrapper.get('input')
    const marker = wrapper.get('.ui-field-required')

    expect(marker.text()).toBe('*')
    expect(marker.attributes('aria-hidden')).toBe('true')
    expect(input.attributes('required')).toBeDefined()
    expect(input.element.validity.valueMissing).toBe(true)
  })

  it('keeps native pattern validation semantics on the control', async () => {
    const wrapper = mountField({
      fieldProps: { label: '数字' },
      inner: '<UiInput pattern="^[0-9]+$" />'
    })
    const input = wrapper.get('input')

    await input.setValue('abc')
    expect(input.element.validity.patternMismatch).toBe(true)

    await input.setValue('123')
    expect(input.element.validity.valid).toBe(true)
  })

  it('wires UiTextarea controls the same way', () => {
    const wrapper = mountField({
      fieldProps: { label: '简介', help: '200 字以内', required: true },
      inner: '<UiTextarea rows="4" />'
    })
    const textarea = wrapper.get('textarea')

    expect(wrapper.get('label').attributes('for')).toBe(textarea.attributes('id'))
    expect(textarea.attributes('aria-describedby')).toBe(wrapper.get('.ui-field-help').attributes('id'))
    expect(textarea.attributes('required')).toBeDefined()
  })

  it('exposes slot props for controls that wire the association manually', () => {
    const wrapper = mount({
      components: { UiField },
      template: `
        <UiField v-slot="{ controlId, describedBy, invalid, required }" label="验证码" help="点击图片刷新">
          <div class="captcha-row">
            <input
              :id="controlId"
              :aria-describedby="describedBy || undefined"
              :aria-invalid="invalid || undefined"
              :required="required || undefined"
            />
            <img alt="验证码" />
          </div>
        </UiField>`
    })
    const input = wrapper.get('input')

    expect(wrapper.get('label').attributes('for')).toBe(input.attributes('id'))
    expect(input.attributes('aria-describedby')).toBe(wrapper.get('.ui-field-help').attributes('id'))
    expect(input.attributes('aria-invalid')).toBeUndefined()
    expect(input.attributes('required')).toBeUndefined()
  })

  it('merges an explicit aria-describedby with the field descriptions', () => {
    const wrapper = mount({
      components: { UiField, UiInput },
      template: `
        <UiField label="备注" help="选填">
          <UiInput aria-describedby="external-hint" />
        </UiField>`
    })
    const describedby = wrapper.get('input').attributes('aria-describedby')?.split(' ')

    expect(describedby).toContain('external-hint')
    expect(describedby).toContain(wrapper.get('.ui-field-help').attributes('id'))
  })

  it('round-trips v-model with modifiers through the field slot', async () => {
    const wrapper = mount({
      components: { UiField, UiInput },
      data: () => ({ username: '' }),
      template: `
        <UiField label="用户名">
          <UiInput v-model.trim="username" placeholder="请输入用户名" />
        </UiField>`
    })

    await wrapper.get('input').setValue('  alice  ')

    expect(wrapper.vm.username).toBe('alice')
  })

  it('keeps the natural tab order and keyboard focusability', () => {
    const wrapper = mount(
      {
        components: { UiField, UiInput },
        template: `
          <form>
            <UiField label="用户名"><UiInput /></UiField>
            <UiField label="密码"><UiInput type="password" /></UiField>
            <button type="submit">登录</button>
          </form>`
      },
      { attachTo: document.body }
    )

    const ordered = Array.from(wrapper.element.querySelectorAll('label, input, button'))
    expect(ordered.map((el) => el.tagName.toLowerCase())).toEqual(['label', 'input', 'label', 'input', 'button'])

    const inputs = wrapper.findAll('input')
    for (const input of inputs) {
      expect(input.attributes('tabindex')).toBeUndefined()
    }

    inputs[0].element.focus()
    expect(document.activeElement).toBe(inputs[0].element)
    inputs[1].element.focus()
    expect(document.activeElement).toBe(inputs[1].element)
  })
})
