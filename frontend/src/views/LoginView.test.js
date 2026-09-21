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
  login: vi.fn(),
  issueCaptcha: vi.fn()
}))

import LoginView from './LoginView.vue'
import { ensureSessionReady } from '../auth/session'
import { issueCaptcha, login } from '../api/services/authService'
import { useAuthStore } from '../stores/auth'

// 类型别名：vi.mock 替换后的服务/会话函数在本测试里只按 Mock 使用（宽松 payload 不再受真实签名约束）。
const ensureSessionReadyMock = /** @type {import('vitest').Mock} */ (ensureSessionReady)
const issueCaptchaMock = /** @type {import('vitest').Mock} */ (issueCaptcha)
const loginMock = /** @type {import('vitest').Mock} */ (login)

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

describe('LoginView', () => {
  function mountView() {
    const pinia = createPinia()
    setActivePinia(pinia)

    return mount(LoginView, {
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
    ensureSessionReadyMock.mockReset()
    issueCaptchaMock.mockReset()
    loginMock.mockReset()
  })

  it('does not trim password before sending login request', async () => {
    ensureSessionReadyMock.mockResolvedValueOnce({ state: 'authenticated' })
    loginMock.mockResolvedValueOnce({ data: { accessToken: 'access-token' }, traceId: 'trace-login' })

    const wrapper = mountView()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('alice')
    await inputs[1].setValue(' secret12 ')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(login).toHaveBeenCalledWith('alice', ' secret12 ', {})
    expect(routerState.replace).toHaveBeenCalledWith({ name: 'posts' })
  })

  it('atomically clears the previous account profile before loading the new session', async () => {
    loginMock.mockResolvedValueOnce({ data: { accessToken: 'new-token' }, traceId: 'trace-login' })

    const wrapper = mountView()
    const auth = useAuthStore()
    auth.installSession({ accessToken: 'old-token' })
    auth.setMe({ userId: 7, username: 'alice' })
    const installSession = vi.spyOn(auth, 'installSession')
    let profileSeenByBootstrap = 'not-called'
    ensureSessionReadyMock.mockImplementationOnce(async (options) => {
      profileSeenByBootstrap = options?.auth?.me ?? null
      return { state: 'ready' }
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('bob')
    await inputs[1].setValue('secret12')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(installSession).toHaveBeenCalledWith({ accessToken: 'new-token', me: null })
    expect(profileSeenByBootstrap).toBeNull()
    expect(auth.accessToken).toBe('new-token')
    expect(auth.me).toBeNull()
  })

  it('refreshes captcha when backend response body says captcha is required', async () => {
    loginMock.mockRejectedValueOnce(backendError(10005, '需要验证码'))
    issueCaptchaMock.mockResolvedValueOnce(captchaResponse('captcha-new', 'new-image', 'trace-captcha'))

    const wrapper = mountView()
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('alice')
    await inputs[1].setValue('secret12')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(issueCaptcha).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('需要验证码')
    expect(wrapper.get('img[alt="验证码"]').attributes('src')).toBe('data:image/png;base64,new-image')
  })
})
