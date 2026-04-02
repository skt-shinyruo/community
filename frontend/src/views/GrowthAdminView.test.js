// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import GrowthAdminView from './GrowthAdminView.vue'

describe('GrowthAdminView', () => {
  it('renders a legacy wallet-admin transition surface', () => {
    const wrapper = mount(GrowthAdminView, {
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

    expect(wrapper.text()).toContain('后台入口已迁移')
    expect(wrapper.text()).toContain('钱包后台')
    expect(wrapper.text()).toContain('冻结钱包')
    expect(wrapper.text()).toContain('回滚交易')

    const links = wrapper.findAll('a')
    expect(links.some((link) => link.text().includes('前往钱包后台'))).toBe(true)
  })
})
