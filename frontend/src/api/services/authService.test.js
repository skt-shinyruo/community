import { afterEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { register, resendRegisterCode, verifyRegisterCode } from './authService'

describe('api/services/authService', () => {
  let mock

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('register should preserve the new email-code response shape', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onPost('/api/auth/register').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: {
        userId: 7,
        emailCodeIssued: true,
        maskedEmail: 'a***e@example.com',
        debugEmailCode: '123456'
      },
      traceId: 'trace-register',
      timestamp: 1774060182920
    })

    const resp = await register({
      username: 'alice',
      password: 'secret',
      email: 'alice@example.com',
      captchaId: 'cid',
      captchaCode: 'abcd'
    })

    expect(resp.traceId).toBe('trace-register')
    expect(resp.data).toEqual({
      userId: 7,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })
  })

  it('resendRegisterCode should post to the resend endpoint', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onPost('/api/auth/register/code/resend').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        userId: 7,
        captchaId: 'cid',
        captchaCode: 'abcd'
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          issued: true,
          maskedEmail: 'a***e@example.com',
          debugEmailCode: '654321'
        },
        traceId: 'trace-resend',
        timestamp: 1774060182920
      }]
    })

    const resp = await resendRegisterCode(7, { captchaId: 'cid', captchaCode: 'abcd' })

    expect(resp.traceId).toBe('trace-resend')
    expect(resp.data.issued).toBe(true)
    expect(resp.data.debugEmailCode).toBe('654321')
  })

  it('verifyRegisterCode should return the login response contract', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onPost('/api/auth/register/code/verify').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        userId: 7,
        code: '123456'
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          accessToken: 'access-token'
        },
        traceId: 'trace-verify',
        timestamp: 1774060182920
      }]
    })

    const resp = await verifyRegisterCode(7, '123456')

    expect(resp.traceId).toBe('trace-verify')
    expect(resp.data).toEqual({
      accessToken: 'access-token'
    })
  })
})
