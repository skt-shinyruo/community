// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/services/marketService', () => ({
  listMarketAddresses: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-list' }),
  createMarketAddress: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-create' }),
  updateMarketAddress: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-update' }),
  deleteMarketAddress: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-delete' })
}))

import MarketAddressesView from './MarketAddressesView.vue'
import {
  createMarketAddress,
  deleteMarketAddress,
  listMarketAddresses,
  updateMarketAddress
} from '../api/services/marketService'

function mountOptions() {
  return {
    global: {
      stubs: {
        UiBreadcrumb: {
          template: '<div><slot /></div>'
        },
        UiCard: {
          template: '<section><slot /></section>'
        },
        UiPageHeader: {
          template: '<header><slot name="title" /><slot name="subtitle" /><slot /></header>'
        },
        UiEmpty: {
          props: ['type'],
          template: '<div><slot /><slot name="description" /></div>'
        },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        }
      }
    }
  }
}

describe('MarketAddressesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listMarketAddresses.mockResolvedValue({ data: [], traceId: 'trace-list' })
    createMarketAddress.mockResolvedValue({ data: {}, traceId: 'trace-create' })
    updateMarketAddress.mockResolvedValue({ data: {}, traceId: 'trace-update' })
    deleteMarketAddress.mockResolvedValue({ data: {}, traceId: 'trace-delete' })
  })

  it('loads addresses on mount and renders existing rows', async () => {
    listMarketAddresses.mockResolvedValue({
      data: [
        {
          addressId: 41,
          receiverName: '张三',
          receiverPhone: '13800000000',
          city: '上海市',
          district: '浦东新区',
          detailAddress: '世纪大道 100 号',
          isDefault: true
        }
      ],
      traceId: 'trace-list'
    })

    const wrapper = mount(MarketAddressesView, mountOptions())
    await flushPromises()

    expect(listMarketAddresses).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('默认地址')
  })

  it('creates, updates, and deletes addresses through the unified service', async () => {
    listMarketAddresses.mockResolvedValue({
      data: [
        {
          addressId: 41,
          receiverName: '张三',
          receiverPhone: '13800000000',
          province: '上海市',
          city: '上海市',
          district: '浦东新区',
          detailAddress: '世纪大道 100 号',
          postalCode: '200120',
          isDefault: true
        }
      ],
      traceId: 'trace-list'
    })

    const wrapper = mount(MarketAddressesView, mountOptions())
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('李四')
    await inputs[1].setValue('13900000000')
    await inputs[2].setValue('北京市')
    await inputs[3].setValue('北京市')
    await inputs[4].setValue('海淀区')
    await inputs[5].setValue('中关村 1 号')
    await inputs[6].setValue('100080')
    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()

    expect(createMarketAddress).toHaveBeenCalledWith(expect.objectContaining({
      receiverName: '李四',
      receiverPhone: '13900000000',
      city: '北京市'
    }))

    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(updateMarketAddress).toHaveBeenCalledWith(41, expect.objectContaining({ receiverName: '张三' }))

    await wrapper.findAll('button')[2].trigger('click')
    await flushPromises()
    expect(deleteMarketAddress).toHaveBeenCalledWith(41)
  })
})
