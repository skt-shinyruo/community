// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/adminGrowthService', () => ({
  searchAdminGrowthUser: vi.fn(),
  listAdminGrowthLedgers: vi.fn(),
  listAdminGrowthAdjustments: vi.fn(),
  adjustAdminGrowth: vi.fn()
}))

import GrowthAdminView from './GrowthAdminView.vue'
import UiCheckbox from '../components/ui/UiCheckbox.vue'
import {
  adjustAdminGrowth,
  listAdminGrowthAdjustments,
  listAdminGrowthLedgers,
  searchAdminGrowthUser
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

    await wrapper.getComponent(UiCheckbox).vm.$emit('update:modelValue', true)
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
})
