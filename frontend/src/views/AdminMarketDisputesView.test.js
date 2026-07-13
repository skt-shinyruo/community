// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { adminResolveMarketDispute, listAdminMarketDisputes } = vi.hoisted(() => ({
  adminResolveMarketDispute: vi.fn(),
  listAdminMarketDisputes: vi.fn()
}))

vi.mock('../api/services/marketService', () => ({
  adminResolveMarketDispute,
  listAdminMarketDisputes
}))

import AdminMarketDisputesView from './AdminMarketDisputesView.vue'

function mountView() {
  return mount(AdminMarketDisputesView, {
    global: {
      stubs: {
        UiBreadcrumb: { template: '<nav><slot /></nav>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot /></header>' }
      }
    }
  })
}

describe('AdminMarketDisputesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listAdminMarketDisputes.mockResolvedValue({
      data: [
        {
          disputeId: '11111111-1111-7111-8111-111111111111',
          goodsType: 'PHYSICAL',
          reason: '货不对板',
          status: 'SELLER_REJECTED',
          buyerNote: '收到的商品与描述不符',
          sellerNote: '不同意退款'
        }
      ],
      traceId: 'trace-disputes'
    })
    adminResolveMarketDispute.mockResolvedValue({ data: {}, traceId: 'trace-resolve' })
  })

  it('renders dispute labels and forwards the chosen resolution action', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('待管理员裁定')
    expect(wrapper.text()).toContain('需要管理员裁定')
    expect(wrapper.text()).toContain('收到的商品与描述不符')
    expect(wrapper.text()).toContain('不同意退款')
    expect(wrapper.text()).not.toContain('旧奖励后台')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(adminResolveMarketDispute).toHaveBeenCalledWith('11111111-1111-7111-8111-111111111111', 'refund', { note: 'refund' })
  })
})
