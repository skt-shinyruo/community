import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import * as marketService from './marketService'

describe('api/services/marketService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('listMarketListings should read the unified listings endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/market/listings').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          listingId: 11,
          goodsType: 'VIRTUAL',
          title: 'Netflix 卡密',
          status: 'ACTIVE'
        },
        {
          listingId: 21,
          goodsType: 'PHYSICAL',
          title: '二手键盘',
          status: 'ACTIVE'
        }
      ],
      traceId: 'trace-market-list',
      timestamp: 1774060182920
    })

    const resp = await marketService.listMarketListings()

    expect(resp.traceId).toBe('trace-market-list')
    expect(resp.data).toHaveLength(2)
    expect(resp.data[0].goodsType).toBe('VIRTUAL')
    expect(resp.data[1].goodsType).toBe('PHYSICAL')
  })

  it('createMarketOrder should write the unified orders endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/market/orders').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        listingId: '22222222-2222-7222-8222-222222222222',
        quantity: 1,
        addressId: '33333333-3333-7333-8333-333333333333'
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          orderId: 31,
          goodsType: 'PHYSICAL',
          status: 'ESCROWED'
        },
        traceId: 'trace-create-order',
        timestamp: 1774060182920
      }]
    })

    const resp = await marketService.createMarketOrder({
      listingId: '22222222-2222-7222-8222-222222222222',
      quantity: 1,
      addressId: '33333333-3333-7333-8333-333333333333'
    })

    expect(resp.traceId).toBe('trace-create-order')
    expect(resp.data.orderId).toBe(31)
    expect(resp.data.goodsType).toBe('PHYSICAL')
  })

  it('deliverMarketOrder should post manual virtual delivery content', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/market/orders/31/deliver').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        deliveryContent: 'card-secret-123'
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          orderId: 31,
          goodsType: 'VIRTUAL',
          status: 'DELIVERED'
        },
        traceId: 'trace-deliver-order',
        timestamp: 1774060182920
      }]
    })

    const resp = await marketService.deliverMarketOrder(31, {
      deliveryContent: 'card-secret-123'
    })

    expect(resp.traceId).toBe('trace-deliver-order')
    expect(resp.data.status).toBe('DELIVERED')
  })

  it('shipMarketOrder should post physical shipment information', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/market/orders/32/ship').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        carrierName: '顺丰',
        trackingNo: 'SF1234567890',
        shippingRemark: '工作日派送'
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          orderId: 32,
          goodsType: 'PHYSICAL',
          status: 'SHIPPED'
        },
        traceId: 'trace-ship-order',
        timestamp: 1774060182920
      }]
    })

    const resp = await marketService.shipMarketOrder(32, {
      carrierName: '顺丰',
      trackingNo: 'SF1234567890',
      shippingRemark: '工作日派送'
    })

    expect(resp.traceId).toBe('trace-ship-order')
    expect(resp.data.status).toBe('SHIPPED')
  })

  it('confirmMarketOrder should post buyer confirmation without payload', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/market/orders/33/confirm').reply((config) => {
      expect(config.data).toBeUndefined()
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          orderId: 33,
          status: 'RELEASE_PENDING'
        },
        traceId: 'trace-confirm-order',
        timestamp: 1774060182920
      }]
    })

    const resp = await marketService.confirmMarketOrder(33)

    expect(resp.traceId).toBe('trace-confirm-order')
    expect(resp.data.status).toBe('RELEASE_PENDING')
  })

  it('cancelMarketOrder should post buyer cancellation without payload', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/market/orders/34/cancel').reply((config) => {
      expect(config.data).toBeUndefined()
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          orderId: 34,
          status: 'REFUND_PENDING'
        },
        traceId: 'trace-cancel-order',
        timestamp: 1774060182920
      }]
    })

    const resp = await marketService.cancelMarketOrder(34)

    expect(resp.traceId).toBe('trace-cancel-order')
    expect(resp.data.status).toBe('REFUND_PENDING')
  })

  it('openMarketOrderDispute should post buyer dispute details', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/market/orders/35/disputes').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        reason: '未收到商品',
        buyerNote: '物流一直没有更新'
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          disputeId: 41,
          orderId: 35,
          status: 'OPEN'
        },
        traceId: 'trace-open-dispute',
        timestamp: 1774060182920
      }]
    })

    const resp = await marketService.openMarketOrderDispute(35, {
      reason: '未收到商品',
      buyerNote: '物流一直没有更新'
    })

    expect(resp.traceId).toBe('trace-open-dispute')
    expect(resp.data.status).toBe('OPEN')
  })

  it('listMarketAddresses should read the unified address endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/market/addresses').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: [
        {
          addressId: 41,
          receiverName: '张三',
          receiverPhone: '13800000000',
          city: '上海市'
        }
      ],
      traceId: 'trace-addresses',
      timestamp: 1774060182920
    })

    const resp = await marketService.listMarketAddresses()

    expect(resp.traceId).toBe('trace-addresses')
    expect(resp.data[0].addressId).toBe(41)
    expect(resp.data[0].receiverName).toBe('张三')
  })

  it('createMarketAddress should send defaultAddress only', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/market/addresses').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        receiverName: '李四',
        receiverPhone: '13900000000',
        province: '北京市',
        city: '北京市',
        district: '海淀区',
        detailAddress: '中关村 1 号',
        postalCode: '100080',
        defaultAddress: true
      })
      return [200, { code: 0, message: 'OK', data: { addressId: '33333333-3333-7333-8333-333333333333' }, traceId: 'trace-create-address' }]
    })

    const resp = await marketService.createMarketAddress({
      receiverName: '李四',
      receiverPhone: '13900000000',
      province: '北京市',
      city: '北京市',
      district: '海淀区',
      detailAddress: '中关村 1 号',
      postalCode: '100080',
      defaultAddress: true
    })

    expect(resp.traceId).toBe('trace-create-address')
  })
})
