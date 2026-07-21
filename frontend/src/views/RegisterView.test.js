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
import { ensureSessionReady } from '../auth/session'
import { issueCaptcha, register, verifyRegisterCode } from '../api/services/authService'
import { useAuthStore } from '../stores/auth'

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
    verifyRegisterCode.mockReset()
    ensureSessionReady.mockReset()
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

  it('does not trim password before sending registration request', async () => {
    issueCaptcha
      .mockResolvedValueOnce(captchaResponse('captcha-old', 'old-image', 'trace-old'))
      .mockResolvedValueOnce(captchaResponse('captcha-new', 'new-image', 'trace-new'))
    register.mockResolvedValueOnce({
      data: {
        userId: '11111111-1111-7111-8111-111111111111',
        registrationToken: 'reg-token',
        emailCodeIssued: true,
        maskedEmail: 'a***e@example.com',
        debugEmailCode: ''
      },
      traceId: 'trace-register'
    })

    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('alice')
    await inputs[1].setValue('alice@example.com')
    await inputs[2].setValue(' secret12 ')
    await inputs[3].setValue('abcd')

    await wrapper.find('button.auth-submit-btn').trigger('click')
    await flushPromises()

    expect(register).toHaveBeenCalledWith({
      username: 'alice',
      password: ' secret12 ',
      email: 'alice@example.com',
      captchaId: 'captcha-old',
      captchaCode: 'abcd'
    })
  })

  it('keeps the email-code verification area separate from the resend captcha area after registration succeeds', async () => {
    issueCaptcha
      .mockResolvedValueOnce(captchaResponse('captcha-old', 'old-image', 'trace-old'))
      .mockResolvedValueOnce(captchaResponse('captcha-new', 'new-image', 'trace-new'))
    register.mockResolvedValueOnce({
      data: {
        userId: '11111111-1111-7111-8111-111111111111',
        registrationToken: 'reg-token',
        emailCodeIssued: true,
        maskedEmail: 'a***e@example.com',
        debugEmailCode: '123456'
      },
      traceId: 'trace-register'
    })

    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('alice')
    await inputs[1].setValue('alice@example.com')
    await inputs[2].setValue('secret')
    await inputs[3].setValue('abcd')

    await wrapper.find('button.auth-submit-btn').trigger('click')
    await flushPromises()

    expect(wrapper.find('.verify-main').exists()).toBe(true)
    expect(wrapper.find('.verify-resend').exists()).toBe(true)
    expect(wrapper.text()).toContain('输入邮箱验证码')
    expect(wrapper.text()).toContain('重新发送验证码')
    expect(wrapper.text()).toContain('如果没收到邮件，先完成下面的图形验证码，再点击重新发送。')
    expect(wrapper.text()).toContain('图形验证码（重发用）')
    expect(wrapper.text()).not.toContain('重发图形验证码')
  })

  it('atomically clears the previous account profile before loading the verified session', async () => {
    window.localStorage.setItem('community.register.pending', JSON.stringify({
      registrationToken: 'reg-token',
      emailCodeIssued: true,
      maskedEmail: 'b***b@example.com'
    }))
    issueCaptcha.mockResolvedValueOnce(captchaResponse('captcha-id', 'image', 'trace-captcha'))
    verifyRegisterCode.mockResolvedValueOnce({
      data: { accessToken: 'new-token' },
      traceId: 'trace-verify'
    })

    const wrapper = mountView()
    await flushPromises()
    const auth = useAuthStore()
    auth.setAccessToken('old-token')
    auth.setMe({ userId: 7, username: 'alice' })
    const installSession = vi.spyOn(auth, 'installSession')
    let profileSeenByBootstrap = 'not-called'
    ensureSessionReady.mockImplementationOnce(async ({ auth: currentAuth }) => {
      profileSeenByBootstrap = currentAuth.me
      return { state: 'ready' }
    })

    await wrapper.get('input[placeholder="请输入邮箱验证码"]').setValue('123456')
    await wrapper.get('.verify-main .auth-submit-btn').trigger('click')
    await flushPromises()

    expect(verifyRegisterCode).toHaveBeenCalledWith('reg-token', '123456')
    expect(installSession).toHaveBeenCalledWith({ accessToken: 'new-token', me: null })
    expect(profileSeenByBootstrap).toBeNull()
    expect(auth.accessToken).toBe('new-token')
    expect(auth.me).toBeNull()
  })
})
