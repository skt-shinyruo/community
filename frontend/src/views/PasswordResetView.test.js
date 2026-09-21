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

// 类型别名：vi.mock 替换后的服务函数在本测试里只按 Mock 使用（宽松 payload 不再受真实签名约束）。
const issueCaptchaMock = /** @type {import('vitest').Mock} */ (issueCaptcha)
const requestPasswordResetMock = /** @type {import('vitest').Mock} */ (requestPasswordReset)
const confirmPasswordResetMock = /** @type {import('vitest').Mock} */ (confirmPasswordReset)

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
    issueCaptchaMock.mockReset()
    requestPasswordResetMock.mockReset()
    confirmPasswordResetMock.mockReset()
  })

  it('does not trim new password before sending confirmation request', async () => {
    routerState.route.query = { token: 'reset-token' }
    issueCaptchaMock.mockResolvedValueOnce(captchaResponse('captcha-old', 'old-image', 'trace-captcha'))
    confirmPasswordResetMock.mockResolvedValueOnce({ data: true, traceId: 'trace-confirm' })

    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue(' secret12 ')
    await inputs[1].setValue('abcd')

    const resetButton = wrapper.findAll('button').find((button) => button.text().includes('重置密码'))
    if (!resetButton) throw new Error('重置密码按钮未找到')
    await resetButton.trigger('click')
    await flushPromises()

    expect(confirmPasswordReset).toHaveBeenCalledWith('reset-token', ' secret12 ', {
      captchaId: 'captcha-old',
      captchaCode: 'abcd'
    })
  })

  it('refreshes captcha when password reset request receives backend captcha error', async () => {
    issueCaptchaMock
      .mockResolvedValueOnce(captchaResponse('captcha-old', 'old-image', 'trace-old'))
      .mockResolvedValueOnce(captchaResponse('captcha-new', 'new-image', 'trace-new'))
    requestPasswordResetMock.mockRejectedValueOnce(backendError(10006, '验证码不正确或已失效'))

    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('alice@example.com')
    await inputs[1].setValue('abcd')

    const sendButton = wrapper.findAll('button').find((button) => button.text().includes('发送重置链接'))
    if (!sendButton) throw new Error('发送重置链接按钮未找到')
    await sendButton.trigger('click')
    await flushPromises()

    expect(issueCaptcha).toHaveBeenCalledTimes(2)
    expect(wrapper.get('img[alt="验证码"]').attributes('src')).toBe('data:image/png;base64,new-image')
    expect(wrapper.text()).toContain('验证码不正确或已失效')
  })
})
