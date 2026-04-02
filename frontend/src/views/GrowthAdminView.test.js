// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/adminGrowthService', () => ({
  searchAdminGrowthUser: vi.fn(),
  listAdminGrowthLedgers: vi.fn(),
  listAdminGrowthAdjustments: vi.fn(),
  adjustAdminGrowth: vi.fn(),
  getUserLevelConfig: vi.fn(),
  updateUserLevelConfig: vi.fn()
}))

import GrowthAdminView from './GrowthAdminView.vue'
import UiCheckbox from '../components/ui/UiCheckbox.vue'
import {
  adjustAdminGrowth,
  getUserLevelConfig,
  listAdminGrowthAdjustments,
  listAdminGrowthLedgers,
  searchAdminGrowthUser,
  updateUserLevelConfig
} from '../api/services/adminGrowthService'

describe('GrowthAdminView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    searchAdminGrowthUser.mockResolvedValue({
      data: {
        userId: 7,
        username: 'alice',
        score: 12,
        level: 2,
        rewardBalance: 8,
        frozenBalance: 0
      }
    })
    listAdminGrowthLedgers.mockResolvedValue({ data: [] })
    listAdminGrowthAdjustments.mockResolvedValue({ data: [] })
    adjustAdminGrowth.mockResolvedValue({
      data: {
        userId: 7,
        username: 'alice',
        score: 13,
        level: 2,
        rewardBalance: 8,
        frozenBalance: 0
      }
    })
    getUserLevelConfig.mockResolvedValue({
      data: {
        windowDays: 100,
        lv2SignInDays: 12,
        lv3SignInDays: 88,
        enabled: true
      }
    })
    updateUserLevelConfig.mockResolvedValue({
      data: {
        windowDays: 120,
        lv2SignInDays: 20,
        lv3SignInDays: 90,
        enabled: false
      }
    })
  })

  it('submits the confirmation checkbox state through the shared checkbox component', async () => {
    const wrapper = mount(GrowthAdminView, {
      global: {
        stubs: {
          UiBreadcrumb: true,
          RouterLink: {
            template: '<a><slot /></a>'
          }
        }
      }
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('7')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    await wrapper
      .findAllComponents(UiCheckbox)
      .find((component) => component.props('label') === '已确认')
      ?.vm.$emit('update:modelValue', true)
    await wrapper.get('input[placeholder="变更值，如 5 或 -3"]').setValue('5')
    await wrapper.get('input[placeholder="原因"]').setValue('manual adjust')
    await wrapper.findAll('button').find((button) => button.text().includes('执行调账'))?.trigger('click')
    await flushPromises()

    expect(adjustAdminGrowth).toHaveBeenCalledWith(
      expect.objectContaining({
        confirm: true
      })
    )
  })

  it('loads user level config on mount and submits updated config payload', async () => {
    const wrapper = mount(GrowthAdminView, {
      global: {
        stubs: {
          UiBreadcrumb: true,
          RouterLink: {
            template: '<a><slot /></a>'
          }
        }
      }
    })
    await flushPromises()

    expect(getUserLevelConfig).toHaveBeenCalledTimes(1)
    expect(wrapper.get('input[placeholder="签到窗口天数"]').element.value).toBe('100')
    expect(wrapper.get('input[placeholder="LV2 签到门槛"]').element.value).toBe('12')
    expect(wrapper.get('input[placeholder="LV3 签到门槛"]').element.value).toBe('88')

    await wrapper.get('input[placeholder="签到窗口天数"]').setValue('120')
    await wrapper.get('input[placeholder="LV2 签到门槛"]').setValue('20')
    await wrapper.get('input[placeholder="LV3 签到门槛"]').setValue('90')
    await wrapper
      .findAllComponents(UiCheckbox)
      .find((component) => component.props('label') === '启用规则')
      ?.vm.$emit('update:modelValue', false)
    await wrapper.findAll('button').find((button) => button.text().includes('保存规则'))?.trigger('click')
    await flushPromises()

    expect(updateUserLevelConfig).toHaveBeenCalledWith({
      windowDays: 120,
      lv2SignInDays: 20,
      lv3SignInDays: 90,
      enabled: false
    })
  })

  it('shows config load error and keeps defaults when initial config load fails', async () => {
    getUserLevelConfig.mockRejectedValueOnce(new Error('配置加载失败'))
    const wrapper = mount(GrowthAdminView, {
      global: {
        stubs: {
          UiBreadcrumb: true,
          RouterLink: {
            template: '<a><slot /></a>'
          }
        }
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('配置加载失败')
    expect(wrapper.get('input[placeholder="签到窗口天数"]').element.value).toBe('100')
    expect(wrapper.get('input[placeholder="LV2 签到门槛"]').element.value).toBe('12')
    expect(wrapper.get('input[placeholder="LV3 签到门槛"]').element.value).toBe('88')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text().includes('保存规则'))
        ?.attributes('disabled')
    ).toBeDefined()
  })
})
