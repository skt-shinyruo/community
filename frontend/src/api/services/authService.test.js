import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import * as authService from './authService'
import { issueCaptcha, login, register, requestPasswordReset, confirmPasswordReset, resendRegisterCode, verifyRegisterCode } from './authService'
import { setToastHandler } from '../../ui/toastService'

describe('api/services/authService', () => {
  let mock

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('does not expose a refresh entry point outside the shared coordinator', () => {
    expect(authService).not.toHaveProperty('refresh')
  })

  it('register should preserve the new email-code response shape', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onPost('/api/auth/register').reply(200, {
      code: 0,
      message: 'OK',
      httpStatus: 200,
      data: {
        userId: '11111111-1111-7111-8111-111111111111',
        registrationToken: '0123456789abcdef0123456789abcdef',
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
      userId: '11111111-1111-7111-8111-111111111111',
      registrationToken: '0123456789abcdef0123456789abcdef',
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
        registrationToken: 'token',
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

    const resp = await resendRegisterCode('token', { captchaId: 'cid', captchaCode: 'abcd' })

    expect(resp.traceId).toBe('trace-resend')
    expect(resp.data.issued).toBe(true)
    expect(resp.data.debugEmailCode).toBe('654321')
  })

  it('verifyRegisterCode should return the login response contract', async () => {
    setActivePinia(createPinia())
    mock = new MockAdapter(http)
    mock.onPost('/api/auth/register/code/verify').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        registrationToken: 'token',
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

    const resp = await verifyRegisterCode('token', '123456')

    expect(resp.traceId).toBe('trace-verify')
    expect(resp.data).toEqual({
      accessToken: 'access-token'
    })
  })

  describe('auth form global error toast opt-out', () => {
    let toast

    beforeEach(() => {
      setActivePinia(createPinia())
      toast = vi.fn()
      setToastHandler(toast)
    })

    afterEach(() => {
      setToastHandler(null)
    })

    it.each([
      ['login', '/api/auth/login', () => login('alice', 'secret')],
      ['register', '/api/auth/register', () => register({ username: 'alice', password: 'secret', email: 'alice@example.com' })],
      ['resendRegisterCode', '/api/auth/register/code/resend', () => resendRegisterCode('token')],
      ['verifyRegisterCode', '/api/auth/register/code/verify', () => verifyRegisterCode('token', '123456')],
      ['requestPasswordReset', '/api/auth/password/reset/request', () => requestPasswordReset('alice@example.com')],
      ['confirmPasswordReset', '/api/auth/password/reset/confirm', () => confirmPasswordReset('token', 'new-secret')]
    ])('skips the global error toast for %s failures (shown inline by the form)', async (_name, url, invoke) => {
      mock = new MockAdapter(http)
      mock.onPost(url).replyOnce(400, { code: 10001, message: '表单错误', traceId: 'trace-form' })

      await expect(invoke()).rejects.toBeTruthy()
      expect(toast).not.toHaveBeenCalled()
    })

    it('keeps the global error toast for captcha loading failures', async () => {
      mock = new MockAdapter(http)
      mock.onGet('/api/auth/captcha').replyOnce(500, { code: 500, message: '服务异常', traceId: 'trace-captcha' })

      await expect(issueCaptcha()).rejects.toBeTruthy()
      expect(toast).toHaveBeenCalledTimes(1)
    })
  })
})
