// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerState = vi.hoisted(() => ({
  route: {
    query: {}
  },
  push: vi.fn()
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routerState.route,
    useRouter: () => ({
      push: routerState.push
    })
  }
})

vi.mock('../api/services/authService', () => ({
  issueCaptcha: vi.fn(),
  requestPasswordReset: vi.fn(),
  confirmPasswordReset: vi.fn()
}))

import PasswordResetView from './PasswordResetView.vue'
import { confirmPasswordReset, issueCaptcha, requestPasswordReset } from '../api/services/authService'

function captchaResponse(captchaId, imageBase64, traceId) {
  return {
    data: {
      captchaId,
      imageBase64
    },
    traceId
  }
}

function backendError(code, message) {
  return {
    response: {
      status: 400,
      data: {
        code,
        message,
        traceId: 'trace-error'
      }
    },
    message: 'Request failed with status code 400'
  }
}

describe('PasswordResetView', () => {
  function mountView() {
    return mount(PasswordResetView, {
      global: {
        stubs: {
          RouterLink: { template: '<a><slot /></a>' }
        }
      }
    })
  }

  beforeEach(() => {
    routerState.route.query = {}
    routerState.push.mockClear()
    issueCaptcha.mockReset()
    requestPasswordReset.mockReset()
    confirmPasswordReset.mockReset()
  })

  it('does not trim new password before sending confirmation request', async () => {
    routerState.route.query = { token: 'reset-token' }
    issueCaptcha.mockResolvedValueOnce(captchaResponse('captcha-old', 'old-image', 'trace-captcha'))
    confirmPasswordReset.mockResolvedValueOnce({ data: true, traceId: 'trace-confirm' })

    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue(' secret12 ')
    await inputs[1].setValue('abcd')

    await wrapper.findAll('button').find((button) => button.text().includes('重置密码')).trigger('click')
    await flushPromises()

    expect(confirmPasswordReset).toHaveBeenCalledWith('reset-token', ' secret12 ', {
      captchaId: 'captcha-old',
      captchaCode: 'abcd'
    })
  })

  it('refreshes captcha when password reset request receives backend captcha error', async () => {
    issueCaptcha
      .mockResolvedValueOnce(captchaResponse('captcha-old', 'old-image', 'trace-old'))
      .mockResolvedValueOnce(captchaResponse('captcha-new', 'new-image', 'trace-new'))
    requestPasswordReset.mockRejectedValueOnce(backendError(10006, '验证码不正确或已失效'))

    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('alice@example.com')
    await inputs[1].setValue('abcd')

    await wrapper.findAll('button').find((button) => button.text().includes('发送重置链接')).trigger('click')
    await flushPromises()

    expect(issueCaptcha).toHaveBeenCalledTimes(2)
    expect(wrapper.get('img[alt="验证码"]').attributes('src')).toBe('data:image/png;base64,new-image')
    expect(wrapper.text()).toContain('验证码不正确或已失效')
  })
})
