// @vitest-environment jsdom

import { DOMWrapper, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import UiField from './UiField.vue'
import UiSelect from './UiSelect.vue'

const OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'tech', label: '技术' },
  { value: 'life', label: '生活', disabled: true },
  { value: 'news', label: '资讯' }
]

const TYPEAHEAD_OPTIONS = [
  { value: 'banana', label: 'banana' },
  { value: 'blueberry', label: 'blueberry' },
  { value: 'cherry', label: 'cherry', disabled: true },
  { value: 'coconut', label: 'coconut' }
]

const wrappers = []

function track(wrapper) {
  wrappers.push(wrapper)
  return wrapper
}

function mountSelect(props = {}, options = {}) {
  return track(
    mount(UiSelect, {
      attachTo: document.body,
      ...options,
      props: { options: OPTIONS, label: '分类', ...props }
    })
  )
}

function triggerOf(wrapper) {
  return wrapper.get('.ui-select__trigger')
}

function listboxEl() {
  return document.body.querySelector('[role="listbox"]')
}

function optionEls() {
  return [...document.body.querySelectorAll('.ui-select__option')]
}

function activeOptionId(wrapper) {
  return triggerOf(wrapper).attributes('aria-activedescendant')
}

async function openByClick(wrapper) {
  await triggerOf(wrapper).trigger('click')
  await nextTick()
}

function mockRects({ trigger, listbox } = {}) {
  const zero = { top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0 }
  return vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function rects() {
    if (this.classList?.contains('ui-select__trigger')) return { ...zero, ...trigger }
    if (this.classList?.contains('ui-select__listbox')) return { ...zero, ...listbox }
    return zero
  })
}

afterEach(() => {
  while (wrappers.length) wrappers.pop().unmount()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('UiSelect', () => {
  it('exposes select-only combobox semantics with a label plus value accessible name', () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)

    expect(trigger.attributes('role')).toBe('combobox')
    expect(trigger.attributes('aria-haspopup')).toBe('listbox')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(trigger.attributes('aria-controls')).toBeUndefined()
    expect(trigger.attributes('aria-activedescendant')).toBeUndefined()
    expect(listboxEl()).toBeNull()

    const labelEl = wrapper.get('.sr-only')
    const valueEl = wrapper.get('.ui-select__value')
    expect(labelEl.text()).toBe('分类')
    expect(trigger.attributes('aria-labelledby')).toBe(`${labelEl.attributes('id')} ${valueEl.attributes('id')}`)
    expect(valueEl.text()).toBe('请选择')
    expect(valueEl.classes()).toContain('ui-select__value--placeholder')
  })

  it('opens on click, focuses the combobox, and activates the first enabled option', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)

    await openByClick(wrapper)

    const listbox = listboxEl()
    expect(listbox).toBeTruthy()
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(trigger.attributes('aria-controls')).toBe(listbox.id)
    expect(listbox.getAttribute('aria-labelledby')).toBe(wrapper.get('.sr-only').attributes('id'))
    // select-only combobox：DOM 焦点始终留在 combobox 上
    expect(document.activeElement).toBe(trigger.element)

    const options = optionEls()
    expect(options.map((option) => option.textContent)).toEqual(['全部', '技术', '生活', '资讯'])
    expect(options[2].getAttribute('aria-disabled')).toBe('true')
    expect(options.every((option) => option.getAttribute('aria-selected') === 'false')).toBe(true)
    expect(trigger.attributes('aria-activedescendant')).toBe(options[0].id)
    expect(options[0].classList.contains('ui-select__option--active')).toBe(true)
  })

  it('keeps DOM focus on the combobox by preventing mousedown inside the listbox', async () => {
    const wrapper = mountSelect()
    await openByClick(wrapper)

    const event = new MouseEvent('mousedown', { bubbles: true, cancelable: true })
    listboxEl().dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })

  it('closes on a second trigger click', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)

    await openByClick(wrapper)
    expect(listboxEl()).toBeTruthy()

    await trigger.trigger('click')
    expect(listboxEl()).toBeNull()
    expect(trigger.attributes('aria-expanded')).toBe('false')
  })

  it('shows the selected option and activates it when reopened', async () => {
    const wrapper = mountSelect({ modelValue: 'tech' })
    const trigger = triggerOf(wrapper)

    expect(trigger.get('.ui-select__value').text()).toBe('技术')

    await openByClick(wrapper)
    const options = optionEls()
    expect(trigger.attributes('aria-activedescendant')).toBe(options[1].id)
    expect(options[1].getAttribute('aria-selected')).toBe('true')
    expect(options[1].classList.contains('ui-select__option--selected')).toBe(true)
    expect(options[0].getAttribute('aria-selected')).toBe('false')
  })

  it('falls back to the placeholder when the value matches no option', async () => {
    const wrapper = mountSelect({ modelValue: 'unknown' })
    const trigger = triggerOf(wrapper)

    expect(trigger.get('.ui-select__value').text()).toBe('请选择')

    await openByClick(wrapper)
    expect(optionEls().every((option) => option.getAttribute('aria-selected') === 'false')).toBe(true)
  })

  it('opens with ArrowDown on the selected option, ArrowUp on the last enabled when nothing is selected', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await nextTick()
    expect(listboxEl()).toBeTruthy()
    expect(activeOptionId(wrapper)).toBe(optionEls()[0].id)

    await trigger.trigger('keydown', { key: 'Escape' })

    // ArrowUp 打开且未选时定位最后一个可用项（跳过禁用的「生活」）
    await trigger.trigger('keydown', { key: 'ArrowUp' })
    await nextTick()
    expect(activeOptionId(wrapper)).toBe(optionEls()[3].id)

    await trigger.trigger('keydown', { key: 'Escape' })
    await wrapper.setProps({ modelValue: 'tech' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await nextTick()
    expect(activeOptionId(wrapper)).toBe(optionEls()[1].id)
  })

  it('opens with Enter and Space', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)

    await trigger.trigger('keydown', { key: 'Enter' })
    await nextTick()
    expect(listboxEl()).toBeTruthy()

    await trigger.trigger('keydown', { key: 'Escape' })

    await trigger.trigger('keydown', { key: ' ' })
    await nextTick()
    expect(listboxEl()).toBeTruthy()
    expect(activeOptionId(wrapper)).toBe(optionEls()[0].id)
  })

  it('moves the active option with arrows, skipping disabled options and clamping at the edges', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)
    await openByClick(wrapper)

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[1].id)

    // 跳过禁用的「生活」
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[3].id)

    // 末项不循环，停在原位
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[3].id)

    await trigger.trigger('keydown', { key: 'ArrowUp' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[1].id)

    await trigger.trigger('keydown', { key: 'ArrowUp' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[0].id)

    await trigger.trigger('keydown', { key: 'ArrowUp' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[0].id)
  })

  it('jumps to the first and last enabled options with Home and End, only while open', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)

    // 关闭态 Home/End 不打开浮层
    await trigger.trigger('keydown', { key: 'Home' })
    await nextTick()
    expect(listboxEl()).toBeNull()

    await openByClick(wrapper)
    await trigger.trigger('keydown', { key: 'End' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[3].id)

    await trigger.trigger('keydown', { key: 'Home' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[0].id)
  })

  it('selects the active option with Enter or Space, emits update:modelValue and restores focus', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)
    await openByClick(wrapper)

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['tech']])
    expect(listboxEl()).toBeNull()
    expect(document.activeElement).toBe(trigger.element)

    await openByClick(wrapper)
    await trigger.trigger('keydown', { key: ' ' })
    expect(wrapper.emitted('update:modelValue')[1]).toEqual(['all'])
    expect(document.activeElement).toBe(trigger.element)
  })

  it('selects options on click and restores focus to the combobox', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)
    await openByClick(wrapper)

    await new DOMWrapper(optionEls()[3]).trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['news']])
    expect(listboxEl()).toBeNull()
    expect(document.activeElement).toBe(trigger.element)
  })

  it('closes without re-emitting when the current value is committed again', async () => {
    const wrapper = mountSelect({ modelValue: 'tech' })
    await openByClick(wrapper)

    await new DOMWrapper(optionEls()[1]).trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(listboxEl()).toBeNull()
  })

  it('ignores clicks on disabled options and keeps the listbox open', async () => {
    const wrapper = mountSelect()
    await openByClick(wrapper)

    await new DOMWrapper(optionEls()[2]).trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(listboxEl()).toBeTruthy()
  })

  it('moves the active option on hover but skips disabled options', async () => {
    const wrapper = mountSelect()
    await openByClick(wrapper)

    await new DOMWrapper(optionEls()[3]).trigger('mouseover')
    expect(activeOptionId(wrapper)).toBe(optionEls()[3].id)

    await new DOMWrapper(optionEls()[2]).trigger('mouseover')
    expect(activeOptionId(wrapper)).toBe(optionEls()[3].id)
  })

  it('closes on Escape without changing the value and restores focus', async () => {
    const wrapper = mountSelect()
    const trigger = triggerOf(wrapper)
    await openByClick(wrapper)

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Escape' })

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(listboxEl()).toBeNull()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger.element)
  })

  it('closes on Tab without intercepting natural focus movement', async () => {
    const wrapper = mountSelect()
    await openByClick(wrapper)

    await triggerOf(wrapper).trigger('keydown', { key: 'Tab' })

    expect(listboxEl()).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('closes on outside pointerdown but ignores interactions inside the trigger and listbox', async () => {
    const wrapper = mountSelect()
    await openByClick(wrapper)

    listboxEl().dispatchEvent(new Event('pointerdown', { bubbles: true }))
    expect(listboxEl()).toBeTruthy()

    triggerOf(wrapper).element.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    expect(listboxEl()).toBeTruthy()

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()
    expect(listboxEl()).toBeNull()
  })

  it('selects typeahead matches directly while closed, cycling repeated characters and skipping disabled options', async () => {
    const wrapper = mountSelect({ options: TYPEAHEAD_OPTIONS, label: '水果' })
    const trigger = triggerOf(wrapper)

    await trigger.trigger('keydown', { key: 'b' })
    expect(wrapper.emitted('update:modelValue')).toEqual([['banana']])
    expect(listboxEl()).toBeNull()

    // 连续输入同一字符：在匹配项之间循环
    await wrapper.setProps({ modelValue: 'banana' })
    await trigger.trigger('keydown', { key: 'b' })
    expect(wrapper.emitted('update:modelValue')[1]).toEqual(['blueberry'])

    // 禁用项不参与匹配：c 跳过 cherry 命中 coconut
    await trigger.trigger('keydown', { key: 'c' })
    expect(wrapper.emitted('update:modelValue')[2]).toEqual(['coconut'])

    // 无匹配时不发出事件
    await trigger.trigger('keydown', { key: 'x' })
    expect(wrapper.emitted('update:modelValue')).toHaveLength(3)

    // 修饰键组合不触发 typeahead
    await trigger.trigger('keydown', { key: 'b', ctrlKey: true })
    expect(wrapper.emitted('update:modelValue')).toHaveLength(3)
  })

  it('matches multi-character prefixes typed in quick succession', async () => {
    const wrapper = mountSelect({ options: TYPEAHEAD_OPTIONS, label: '水果' })
    const trigger = triggerOf(wrapper)

    await trigger.trigger('keydown', { key: 'b' })
    await trigger.trigger('keydown', { key: 'l' })

    // 关闭态 typeahead 每次击键都直接选中：先命中 banana，再由前缀 "bl" 命中 blueberry
    expect(wrapper.emitted('update:modelValue')).toEqual([['banana'], ['blueberry']])
  })

  it('resets the typeahead buffer after a pause', async () => {
    vi.useFakeTimers()
    const wrapper = mountSelect({ options: TYPEAHEAD_OPTIONS, label: '水果' })
    const trigger = triggerOf(wrapper)

    await trigger.trigger('keydown', { key: 'b' })
    vi.advanceTimersByTime(600)
    await trigger.trigger('keydown', { key: 'c' })

    // 停顿后重新匹配：命中 coconut 而不是查找 "bc"
    expect(wrapper.emitted('update:modelValue')).toEqual([['banana'], ['coconut']])
  })

  it('moves the active option with typeahead while open without changing the value', async () => {
    const wrapper = mountSelect({ options: TYPEAHEAD_OPTIONS, label: '水果' })
    const trigger = triggerOf(wrapper)
    await openByClick(wrapper)

    // 打开时活动项已是 banana，输入 b 循环到下一个匹配项
    await trigger.trigger('keydown', { key: 'b' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[1].id)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    // 再次输入 b 循环回 banana
    await trigger.trigger('keydown', { key: 'b' })
    expect(activeOptionId(wrapper)).toBe(optionEls()[0].id)

    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')).toEqual([['banana']])
    expect(listboxEl()).toBeNull()
  })

  it('does not open or respond when disabled', async () => {
    const wrapper = mountSelect({ disabled: true, clearable: true, modelValue: 'tech' })
    const trigger = triggerOf(wrapper)

    expect(trigger.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.ui-select__clear').exists()).toBe(false)

    await trigger.trigger('click')
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'b' })
    await nextTick()

    expect(listboxEl()).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('renders a disabled empty row for empty options and tolerates navigation keys', async () => {
    const wrapper = mountSelect({ options: [] })
    const trigger = triggerOf(wrapper)
    await openByClick(wrapper)

    const empty = document.body.querySelector('.ui-select__empty')
    expect(empty).toBeTruthy()
    expect(empty.textContent).toBe('暂无可选项')
    expect(empty.getAttribute('role')).toBe('option')
    expect(empty.getAttribute('aria-disabled')).toBe('true')
    expect(trigger.attributes('aria-activedescendant')).toBeUndefined()

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'End' })
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(trigger.attributes('aria-activedescendant')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(listboxEl()).toBeTruthy()

    await trigger.trigger('keydown', { key: 'Escape' })
    expect(listboxEl()).toBeNull()
  })

  it('announces loading with an accessible status, hides options and suspends navigation', async () => {
    const wrapper = mountSelect({ loading: true })
    const trigger = triggerOf(wrapper)
    await openByClick(wrapper)

    const listbox = listboxEl()
    expect(listbox.getAttribute('aria-busy')).toBe('true')
    const status = document.body.querySelector('.ui-select__status')
    expect(status.getAttribute('role')).toBe('status')
    expect(status.textContent).toContain('正在加载选项')
    expect(optionEls()).toHaveLength(0)
    expect(trigger.attributes('aria-activedescendant')).toBeUndefined()

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'b' })
    await trigger.trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(listboxEl()).toBeTruthy()

    await trigger.trigger('keydown', { key: 'Escape' })
    expect(listboxEl()).toBeNull()

    // 关闭态 loading 时 typeahead 不直接改值
    await trigger.trigger('keydown', { key: 'b' })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()

    await wrapper.setProps({ loading: false })
    await openByClick(wrapper)
    expect(listboxEl().getAttribute('aria-busy')).toBeNull()
    expect(optionEls()).toHaveLength(OPTIONS.length)
    expect(trigger.attributes('aria-activedescendant')).toBe(optionEls()[0].id)
  })

  it('clears the selection through the clear button and restores focus to the combobox', async () => {
    const wrapper = mountSelect({ modelValue: 'tech', clearable: true })
    const trigger = triggerOf(wrapper)
    const clear = wrapper.get('.ui-select__clear')

    expect(clear.attributes('aria-label')).toBe('清除选择')

    await openByClick(wrapper)
    await clear.trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([['']])
    expect(listboxEl()).toBeNull()
    expect(document.activeElement).toBe(trigger.element)

    // 父组件同步值后回到占位符，清除按钮消失
    await wrapper.setProps({ modelValue: '' })
    expect(trigger.get('.ui-select__value').text()).toBe('请选择')
    expect(wrapper.find('.ui-select__clear').exists()).toBe(false)
  })

  it('only shows the clear button when clearable with a value present', async () => {
    const wrapper = mountSelect({ modelValue: 'tech' })
    expect(wrapper.find('.ui-select__clear').exists()).toBe(false)

    await wrapper.setProps({ clearable: true })
    expect(wrapper.get('.ui-select__clear').attributes('aria-label')).toBe('清除选择')

    await wrapper.setProps({ clearLabel: '重置筛选' })
    expect(wrapper.get('.ui-select__clear').attributes('aria-label')).toBe('重置筛选')

    await wrapper.setProps({ modelValue: '' })
    expect(wrapper.find('.ui-select__clear').exists()).toBe(false)
  })

  it('round-trips v-model through a stateful parent', async () => {
    const wrapper = track(
      mount({
        components: { UiSelect },
        data: () => ({ picked: '', options: OPTIONS }),
        template: '<UiSelect v-model="picked" :options="options" label="分类" />'
      }, { attachTo: document.body })
    )

    await openByClick(wrapper)
    await new DOMWrapper(optionEls()[1]).trigger('click')

    expect(wrapper.vm.picked).toBe('tech')
    expect(wrapper.get('.ui-select__value').text()).toBe('技术')
  })

  it('renders custom trigger and option content through slots', async () => {
    const wrapper = track(
      mount({
        components: { UiSelect },
        data: () => ({ picked: '', options: OPTIONS }),
        template: `
          <UiSelect v-model="picked" :options="options" label="分类">
            <template #default="{ option }">当前：{{ option ? option.label : '无' }}</template>
            <template #option="{ option, selected }">{{ option.label }}{{ selected ? ' ✓' : '' }}</template>
          </UiSelect>`
      }, { attachTo: document.body })
    )

    expect(wrapper.get('.ui-select__value').text()).toBe('当前：无')

    await openByClick(wrapper)
    expect(optionEls().map((option) => option.textContent)).toEqual(['全部', '技术', '生活', '资讯'])

    await new DOMWrapper(optionEls()[0]).trigger('click')
    expect(wrapper.get('.ui-select__value').text()).toBe('当前：全部')

    await openByClick(wrapper)
    expect(optionEls()[0].textContent).toBe('全部 ✓')
  })

  it('inherits label association, descriptions and validity state inside UiField', () => {
    const wrapper = track(
      mount({
        components: { UiField, UiSelect },
        template: `
          <UiField label="分类" help="按分类筛选" error="必选" required>
            <UiSelect :options="[]" />
          </UiField>`
      })
    )
    const label = wrapper.get('.ui-field-label')
    const trigger = wrapper.get('.ui-select__trigger')

    expect(label.attributes('for')).toBe(trigger.attributes('id'))
    expect(trigger.attributes('aria-describedby').split(' ')).toEqual([
      wrapper.get('.ui-field-help').attributes('id'),
      wrapper.get('.ui-field-error').attributes('id')
    ])
    expect(trigger.attributes('aria-invalid')).toBe('true')
    // combobox 按钮不支持原生 required，映射为 aria-required
    expect(trigger.attributes('aria-required')).toBe('true')
    expect(trigger.attributes('required')).toBeUndefined()
    expect(trigger.attributes('aria-labelledby')).toBe(
      `${label.attributes('id')} ${wrapper.get('.ui-select__value').attributes('id')}`
    )
    // 字段提供名称后不再渲染自带的隐藏 label
    expect(wrapper.find('.ui-select .sr-only').exists()).toBe(false)
  })

  it('positions below the trigger and flips above when there is no room below', async () => {
    const zero = { top: 0, left: 0, right: 0, bottom: 0, width: 0, height: 0 }
    const spy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function rects() {
      if (this.classList?.contains('ui-select__trigger')) {
        return { ...zero, top: 100, left: 50, width: 200, height: 32, bottom: 132, right: 250 }
      }
      if (this.classList?.contains('ui-select__listbox')) {
        return { ...zero, width: 220, height: 160, right: 220, bottom: 160 }
      }
      return zero
    })
    const wrapper = mountSelect()
    await openByClick(wrapper)

    let listbox = listboxEl()
    expect(listbox.classList.contains('ui-select__listbox--bottom')).toBe(true)
    // top = triggerRect.bottom + 间距 = 132 + 4，最小宽度跟随 trigger 宽度
    expect(listbox.style.top).toBe('136px')
    expect(listbox.style.left).toBe('50px')
    expect(listbox.style.minWidth).toBe('200px')

    await triggerOf(wrapper).trigger('keydown', { key: 'Escape' })

    // 触发点贴近视口底部（jsdom innerHeight 默认 768）：下方 36px 放不下 160px 浮层，翻到上方
    spy.mockImplementation(function rects() {
      if (this.classList?.contains('ui-select__trigger')) {
        return { ...zero, top: 700, left: 50, width: 200, height: 32, bottom: 732, right: 250 }
      }
      if (this.classList?.contains('ui-select__listbox')) {
        return { ...zero, width: 220, height: 160, right: 220, bottom: 160 }
      }
      return zero
    })
    await openByClick(wrapper)

    listbox = listboxEl()
    expect(listbox.classList.contains('ui-select__listbox--top')).toBe(true)
    // top = triggerRect.top - 浮层高度 - 间距 = 700 - 160 - 4
    expect(listbox.style.top).toBe('536px')
  })

  it('prefers the top placement and flips down when there is no room above', async () => {
    mockRects({
      trigger: { top: 30, left: 50, width: 200, height: 32, bottom: 62, right: 250 },
      listbox: { width: 220, height: 160, right: 220, bottom: 160 }
    })
    const wrapper = mountSelect({ placement: 'top' })
    await openByClick(wrapper)

    const listbox = listboxEl()
    // 上方只有 30px 空间，翻到下方
    expect(listbox.classList.contains('ui-select__listbox--bottom')).toBe(true)
    expect(listbox.style.top).toBe('66px')
  })

  it('clamps the listbox inside the viewport horizontally', async () => {
    mockRects({
      trigger: { top: 100, left: 960, width: 60, height: 32, bottom: 132, right: 1020 },
      listbox: { width: 160, height: 120, right: 160, bottom: 120 }
    })
    const wrapper = mountSelect()
    await openByClick(wrapper)

    const listbox = listboxEl()
    // jsdom innerWidth 默认 1024：960 + 160 溢出，夹取到 1024 - 160 - 8
    expect(listbox.style.left).toBe('856px')
    expect(listbox.style.top).toBe('136px')
  })
})
