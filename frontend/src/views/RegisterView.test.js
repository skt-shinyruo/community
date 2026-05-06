// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerState = vi.hoisted(() => ({
  route: {
    query: {}
  },
  replace: vi.fn()
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routerState.route,
    useRouter: () => ({
      replace: routerState.replace
    })
  }
})

vi.mock('../auth/session', () => ({
  ensureSessionReady: vi.fn()
}))

vi.mock('../api/services/authService', () => ({
  register: vi.fn(),
  resendRegisterCode: vi.fn(),
  verifyRegisterCode: vi.fn(),
  issueCaptcha: vi.fn()
}))

import RegisterView from './RegisterView.vue'
import { issueCaptcha, register } from '../api/services/authService'

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

describe('RegisterView', () => {
  function mountView() {
    const pinia = createPinia()
    setActivePinia(pinia)

    return mount(RegisterView, {
      global: {
        plugins: [pinia],
        stubs: {
          RouterLink: { template: '<a><slot /></a>' }
        }
      }
    })
  }

  beforeEach(() => {
    routerState.route.query = {}
    routerState.replace.mockClear()
    window.localStorage.clear()
    issueCaptcha.mockReset()
    register.mockReset()
  })

  it('refreshes the captcha after backend rejects registration with an expired captcha', async () => {
    issueCaptcha
      .mockResolvedValueOnce(captchaResponse('captcha-old', 'old-image', 'trace-old'))
      .mockResolvedValueOnce(captchaResponse('captcha-new', 'new-image', 'trace-new'))
    register.mockRejectedValueOnce(backendError(10006, '验证码不正确或已失效'))

    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('alice')
    await inputs[1].setValue('alice@example.com')
    await inputs[2].setValue('secret')
    await inputs[3].setValue('abcd')

    await wrapper.find('button.auth-submit-btn').trigger('click')
    await flushPromises()

    expect(register).toHaveBeenCalledWith({
      username: 'alice',
      password: 'secret',
      email: 'alice@example.com',
      captchaId: 'captcha-old',
      captchaCode: 'abcd'
    })
    expect(issueCaptcha).toHaveBeenCalledTimes(2)
    expect(wrapper.get('img[alt="验证码"]').attributes('src')).toBe('data:image/png;base64,new-image')
    expect(wrapper.findAll('input')[3].element.value).toBe('')
    expect(wrapper.text()).toContain('验证码不正确或已失效')
  })
})
