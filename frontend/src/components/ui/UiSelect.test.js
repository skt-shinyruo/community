// @vitest-environment jsdom

import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import UiSelect from './UiSelect.vue'

const baseOptions = [
  { label: 'All', value: '' },
  { label: 'Posts', value: 'posts' },
  { label: 'Users', value: 'users' }
]

const mountedWrappers = []

function mountSelect(props = {}) {
  const wrapper = mount(UiSelect, {
    attachTo: document.body,
    props: {
      modelValue: '',
      placeholder: 'Select one',
      options: baseOptions,
      ...props
    }
  })

  mountedWrappers.push(wrapper)

  return wrapper
}

function getPanel() {
  return document.body.querySelector('[role="listbox"]')
}

function getOption(value) {
  return document.body.querySelector(`[role="option"][data-value="${String(value)}"]`)
}

afterEach(() => {
  while (mountedWrappers.length > 0) {
    mountedWrappers.pop()?.unmount()
  }

  vi.restoreAllMocks()
})

describe('UiSelect', () => {
  it('renders the selected label when modelValue matches an option', () => {
    const wrapper = mountSelect({ modelValue: 'posts' })

    expect(wrapper.get('.ui-select-label').text()).toBe('Posts')
  })

  it('applies ariaLabel to the trigger', () => {
    const wrapper = mountSelect({ ariaLabel: 'Filter content type' })

    expect(wrapper.get('.ui-select-trigger').attributes('aria-label')).toBe('Filter content type')
  })

  it('renders the placeholder when modelValue is empty', () => {
    const wrapper = mountSelect({
      modelValue: '',
      options: baseOptions.filter((option) => option.value !== '')
    })

    expect(wrapper.get('.ui-select-label').text()).toBe('Select one')
  })

  it('renders the matching empty-string option label instead of the placeholder', () => {
    const wrapper = mountSelect({ modelValue: '' })

    expect(wrapper.get('.ui-select-label').text()).toBe('All')
  })

  it('renders the placeholder when modelValue does not match any option', () => {
    const wrapper = mountSelect({ modelValue: 'missing' })

    expect(wrapper.get('.ui-select-label').text()).toBe('Select one')
  })

  it('syncs a hidden input when name is provided', () => {
    const wrapper = mountSelect({
      modelValue: null,
      name: 'contentType'
    })

    const hiddenInput = wrapper.get('input[type="hidden"]')

    expect(hiddenInput.attributes('name')).toBe('contentType')
    expect(hiddenInput.element.value).toBe('')
  })

  it('disables the trigger and keeps the panel closed', async () => {
    const wrapper = mountSelect({ disabled: true })
    const trigger = wrapper.get('.ui-select-trigger')

    expect(trigger.element.disabled).toBe(true)

    await trigger.trigger('click')

    expect(getPanel()).toBeNull()
  })

  it('closes the panel and prevents selection after disabled flips true', async () => {
    const wrapper = mountSelect()

    await wrapper.get('.ui-select-trigger').trigger('click')
    const option = getOption('users')

    expect(getPanel()).toBeTruthy()

    await wrapper.setProps({ disabled: true })

    expect(getPanel()).toBeNull()

    option?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('change')).toBeUndefined()
  })

  it('emits update:modelValue and change when a user picks an option', async () => {
    const wrapper = mountSelect()

    await wrapper.get('.ui-select-trigger').trigger('click')
    getOption('users')?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()

    expect(wrapper.emitted('update:modelValue')).toEqual([['users']])
    expect(wrapper.emitted('change')).toEqual([['users']])
    expect(getPanel()).toBeNull()
  })

  it('renders the menu in document.body via Teleport', async () => {
    const wrapper = mountSelect()

    await wrapper.get('.ui-select-trigger').trigger('click')

    const panel = getPanel()

    expect(panel).toBeTruthy()
    expect(panel?.parentElement).toBe(document.body)
    expect(wrapper.element.contains(panel)).toBe(false)
  })

  it('highlights the current selected option when opened', async () => {
    const wrapper = mountSelect({ modelValue: 'users' })

    await wrapper.get('.ui-select-trigger').trigger('click')

    expect(getOption('users')?.classList.contains('is-active')).toBe(true)
  })

  it('opens with ArrowDown and skips disabled options', async () => {
    const wrapper = mountSelect({
      options: [
        { label: 'Posts', value: 'posts', disabled: true },
        { label: 'Users', value: 'users' },
        { label: 'Admins', value: 'admins' }
      ]
    })

    await wrapper.get('.ui-select-trigger').trigger('keydown', { key: 'ArrowDown' })

    expect(getPanel()).toBeTruthy()
    expect(getOption('posts')?.classList.contains('is-active')).toBe(false)
    expect(getOption('users')?.classList.contains('is-active')).toBe(true)
  })

  it('opens with Enter from the closed trigger state', async () => {
    const wrapper = mountSelect()

    await wrapper.get('.ui-select-trigger').trigger('keydown', { key: 'Enter' })

    expect(getPanel()).toBeTruthy()
    expect(getOption('')?.classList.contains('is-active')).toBe(true)
  })

  it('opens with Space from the closed trigger state', async () => {
    const wrapper = mountSelect()

    await wrapper.get('.ui-select-trigger').trigger('keydown', { key: ' ' })

    expect(getPanel()).toBeTruthy()
    expect(getOption('')?.classList.contains('is-active')).toBe(true)
  })

  it('opens with ArrowUp and highlights the current value', async () => {
    const wrapper = mountSelect({ modelValue: 'users' })

    await wrapper.get('.ui-select-trigger').trigger('keydown', { key: 'ArrowUp' })

    expect(getPanel()).toBeTruthy()
    expect(getOption('users')?.classList.contains('is-active')).toBe(true)
  })

  it('moves with arrow keys through enabled options only', async () => {
    const wrapper = mountSelect({
      options: [
        { label: 'Alpha', value: 'alpha' },
        { label: 'Bravo', value: 'bravo', disabled: true },
        { label: 'Charlie', value: 'charlie' },
        { label: 'Delta', value: 'delta' }
      ]
    })

    const trigger = wrapper.get('.ui-select-trigger')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(getOption('alpha')?.classList.contains('is-active')).toBe(true)

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    expect(getOption('bravo')?.classList.contains('is-active')).toBe(false)
    expect(getOption('charlie')?.classList.contains('is-active')).toBe(true)

    await trigger.trigger('keydown', { key: 'ArrowUp' })
    expect(getOption('alpha')?.classList.contains('is-active')).toBe(true)
  })

  it('selects the highlighted option with Enter', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('.ui-select-trigger')

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('update:modelValue')).toEqual([['posts']])
    expect(wrapper.emitted('change')).toEqual([['posts']])
    expect(getPanel()).toBeNull()
  })

  it('closes with Escape and restores focus to the trigger', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('.ui-select-trigger')

    trigger.element.focus()
    await trigger.trigger('click')
    trigger.element.blur()

    await trigger.trigger('keydown', { key: 'Escape' })

    expect(getPanel()).toBeNull()
    expect(document.activeElement).toBe(trigger.element)
  })

  it('closes on Tab from the open trigger state without trapping focus', async () => {
    const wrapper = mountSelect()
    const trigger = wrapper.get('.ui-select-trigger')

    await trigger.trigger('click')

    const tabEvent = new KeyboardEvent('keydown', {
      key: 'Tab',
      bubbles: true,
      cancelable: true
    })

    trigger.element.dispatchEvent(tabEvent)
    await nextTick()

    expect(tabEvent.defaultPrevented).toBe(false)
    expect(getPanel()).toBeNull()
  })

  it('keeps teleported options out of the tab order while open', async () => {
    const wrapper = mountSelect()

    await wrapper.get('.ui-select-trigger').trigger('click')

    expect(getOption('')?.tabIndex).toBe(-1)
    expect(getOption('posts')?.tabIndex).toBe(-1)
    expect(getOption('users')?.tabIndex).toBe(-1)
  })

  it('closes when pointerdown happens outside the select', async () => {
    const wrapper = mountSelect()
    const outside = document.createElement('button')
    outside.type = 'button'
    document.body.appendChild(outside)

    await wrapper.get('.ui-select-trigger').trigger('click')

    outside.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await nextTick()

    expect(getPanel()).toBeNull()
  })

  it('closes when outside pointerdown stops propagation', async () => {
    const wrapper = mountSelect()
    const outside = document.createElement('button')
    outside.type = 'button'
    outside.addEventListener('pointerdown', (event) => event.stopPropagation())
    document.body.appendChild(outside)

    await wrapper.get('.ui-select-trigger').trigger('click')

    outside.dispatchEvent(new Event('pointerdown', { bubbles: true, cancelable: true }))
    await nextTick()

    expect(getPanel()).toBeNull()
  })

  it('registers and removes the outside pointerdown listener in capture phase', async () => {
    const addSpy = vi.spyOn(document, 'addEventListener')
    const removeSpy = vi.spyOn(document, 'removeEventListener')
    const wrapper = mountSelect()
    const trigger = wrapper.get('.ui-select-trigger')

    await trigger.trigger('click')

    expect(addSpy).toHaveBeenCalledWith('pointerdown', expect.any(Function), true)

    await trigger.trigger('click')
    await nextTick()

    expect(removeSpy).toHaveBeenCalledWith('pointerdown', expect.any(Function), true)
  })
})
