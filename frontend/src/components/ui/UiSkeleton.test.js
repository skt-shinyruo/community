// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import UiSkeleton from './UiSkeleton.vue'

describe('UiSkeleton', () => {
  it('announces the loading state through role="status" with a sr-only label', () => {
    const wrapper = mount(UiSkeleton)

    expect(wrapper.attributes('role')).toBe('status')
    expect(wrapper.get('.sr-only').text()).toBe('加载中')
    expect(wrapper.classes()).toContain('ui-skeleton--list')
  })

  it('renders the requested number of list rows with blocks hidden from AT', () => {
    const wrapper = mount(UiSkeleton, { props: { rows: 5 } })

    const rows = wrapper.findAll('.ui-skeleton__row')
    expect(rows).toHaveLength(5)
    for (const row of rows) {
      expect(row.attributes('aria-hidden')).toBe('true')
    }
  })

  it('covers the card first-load shape', () => {
    const wrapper = mount(UiSkeleton, { props: { variant: 'card' } })

    expect(wrapper.classes()).toContain('ui-skeleton--card')
    const card = wrapper.get('.ui-skeleton__card')
    expect(card.attributes('aria-hidden')).toBe('true')
    expect(wrapper.findAll('.ui-skeleton__pill')).toHaveLength(2)
    expect(wrapper.find('.ui-skeleton__title').exists()).toBe(true)
    expect(wrapper.find('.ui-skeleton__foot').exists()).toBe(true)
  })

  it('covers the detail first-load shape', () => {
    const wrapper = mount(UiSkeleton, { props: { variant: 'detail' } })

    expect(wrapper.classes()).toContain('ui-skeleton--detail')
    expect(wrapper.find('.ui-skeleton__title--lg').exists()).toBe(true)
    expect(wrapper.findAll('.ui-skeleton__detail .ui-skeleton__line').length).toBeGreaterThanOrEqual(3)
  })

  it('supports a custom accessible label and falls back to list for unknown variants', () => {
    const wrapper = mount(UiSkeleton, {
      props: { label: '评论加载中', variant: 'unknown' }
    })

    expect(wrapper.get('.sr-only').text()).toBe('评论加载中')
    expect(wrapper.classes()).toContain('ui-skeleton--list')
  })
})
