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
        requestId: 'market:req-1',
        listingId: 21,
        quantity: 1,
        addressId: 41
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
      requestId: 'market:req-1',
      listingId: 21,
      quantity: 1,
      addressId: 41
    })

    expect(resp.traceId).toBe('trace-create-order')
    expect(resp.data.orderId).toBe(31)
    expect(resp.data.goodsType).toBe('PHYSICAL')
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
})
