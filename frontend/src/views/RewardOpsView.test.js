// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RewardOpsView from './RewardOpsView.vue'

describe('RewardOpsView', () => {
  it('renders a legacy transition shell instead of the old reward operations console', () => {
    const wrapper = mount(RewardOpsView, {
      global: {
        stubs: {
          UiBreadcrumb: true,
          RouterLink: {
            props: ['to'],
            template: '<a :data-to="JSON.stringify(to)"><slot /></a>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('运营入口已迁移')
    expect(wrapper.text()).toContain('钱包后台')
    expect(wrapper.text()).toContain('历史运营页')
    expect(wrapper.findAll('a').some((link) => link.text().includes('前往钱包后台'))).toBe(true)
  })
})
