import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import * as virtualMarketService from './virtualMarketService'

describe('api/services/virtualMarketService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('listBuyingVirtualOrders should read the buying orders endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/market/virtual/orders/buying').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          orderId: 31,
          requestId: 'buying:req-1',
          listingId: 11,
          listingTitleSnapshot: 'Netflix 卡密',
          status: 'DELIVERED',
          totalAmount: 1500
        }
      ],
      traceId: 'trace-buying-orders',
      timestamp: 1774060182920
    })

    const resp = await virtualMarketService.listBuyingVirtualOrders()

    expect(resp.traceId).toBe('trace-buying-orders')
    expect(resp.data).toEqual([
      {
        orderId: 31,
        requestId: 'buying:req-1',
        listingId: 11,
        listingTitleSnapshot: 'Netflix 卡密',
        status: 'DELIVERED',
        totalAmount: 1500
      }
    ])
  })

  it('listSellingVirtualOrders should read the selling orders endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/market/virtual/orders/selling').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          orderId: 32,
          requestId: 'selling:req-1',
          listingId: 12,
          listingTitleSnapshot: '邀请码',
          status: 'ESCROWED',
          totalAmount: 2400
        }
      ],
      traceId: 'trace-selling-orders',
      timestamp: 1774060182920
    })

    const resp = await virtualMarketService.listSellingVirtualOrders()

    expect(resp.traceId).toBe('trace-selling-orders')
    expect(resp.data).toEqual([
      {
        orderId: 32,
        requestId: 'selling:req-1',
        listingId: 12,
        listingTitleSnapshot: '邀请码',
        status: 'ESCROWED',
        totalAmount: 2400
      }
    ])
  })

  it('getVirtualOrderDetail should read the order detail endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/market/virtual/orders/31').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: {
        orderId: 31,
        requestId: 'buying:req-1',
        listingId: 11,
        listingTitleSnapshot: 'Netflix 卡密',
        status: 'DELIVERED',
        totalAmount: 1500,
        deliveryContents: ['CODE-001', 'CODE-002']
      },
      traceId: 'trace-order-detail',
      timestamp: 1774060182920
    })

    const resp = await virtualMarketService.getVirtualOrderDetail(31)

    expect(resp.traceId).toBe('trace-order-detail')
    expect(resp.data).toEqual({
      orderId: 31,
      requestId: 'buying:req-1',
      listingId: 11,
      listingTitleSnapshot: 'Netflix 卡密',
      status: 'DELIVERED',
      totalAmount: 1500,
      deliveryContents: ['CODE-001', 'CODE-002']
    })
  })

  it('listMyVirtualListings should read the seller listings endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/market/virtual/my-listings').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          listingId: 21,
          sellerUserId: 7,
          title: 'Steam 兑换码',
          description: '库存页继续维护卡密',
          unitPrice: 1999,
          deliveryMode: 'PRELOADED',
          stockMode: 'FINITE',
          stockTotal: 3,
          stockAvailable: 2,
          minPurchaseQuantity: 1,
          maxPurchaseQuantity: 1,
          status: 'ACTIVE'
        }
      ],
      traceId: 'trace-my-listings',
      timestamp: 1774060182920
    })

    const resp = await virtualMarketService.listMyVirtualListings()

    expect(resp.traceId).toBe('trace-my-listings')
    expect(resp.data).toHaveLength(1)
    expect(resp.data[0].listingId).toBe(21)
    expect(resp.data[0].deliveryMode).toBe('PRELOADED')
  })

  it('inventory APIs should preserve list/add/invalidate contracts', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/market/virtual/listings/21/inventory').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          inventoryUnitId: 301,
          listingId: 21,
          sellerUserId: 7,
          payloadType: 'CODE',
          payloadContent: 'CODE-001',
          status: 'AVAILABLE'
        }
      ],
      traceId: 'trace-inventory-list',
      timestamp: 1774060182920
    })
    mock.onPost('/api/market/virtual/listings/21/inventory').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        payloadType: 'CODE',
        payloads: ['CODE-002', 'CODE-003']
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: null,
        traceId: 'trace-inventory-add',
        timestamp: 1774060182920
      }]
    })
    mock.onPost('/api/market/virtual/inventory/301/invalidate').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: null,
      traceId: 'trace-inventory-invalidate',
      timestamp: 1774060182920
    })

    const listResp = await virtualMarketService.listVirtualInventory(21)
    const addResp = await virtualMarketService.addVirtualInventory(21, {
      payloadType: 'CODE',
      payloads: ['CODE-002', 'CODE-003']
    })
    const invalidateResp = await virtualMarketService.invalidateVirtualInventory(301)

    expect(listResp.traceId).toBe('trace-inventory-list')
    expect(listResp.data[0].inventoryUnitId).toBe(301)
    expect(addResp.traceId).toBe('trace-inventory-add')
    expect(addResp.data).toEqual({})
    expect(invalidateResp.traceId).toBe('trace-inventory-invalidate')
    expect(invalidateResp.data).toEqual({})
  })
})
