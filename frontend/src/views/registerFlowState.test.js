import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildRegisterFlowState, clearRegisterFlowState, persistRegisterFlowState, resolveRegisterFlowError, restoreRegisterFlowState } from './registerFlowState'

describe('registerFlowState', () => {
  const registrationToken = '0123456789abcdef0123456789abcdef'

  beforeEach(() => {
    const storage = {
      values: new Map(),
      getItem(key) {
        return this.values.has(key) ? this.values.get(key) : null
      },
      setItem(key, value) {
        this.values.set(key, String(value))
      },
      removeItem(key) {
        this.values.delete(key)
      }
    }
    vi.stubGlobal('window', { localStorage: storage })
  })

  it('stays on the form step when registration has not issued a code yet', () => {
    const state = buildRegisterFlowState()

    expect(state).toMatchObject({
      step: 'form',
      registrationToken: '',
      emailCodeIssued: false,
      maskedEmail: '',
      debugEmailCode: '',
      successMessage: ''
    })
  })

  it('switches to the verify step when registration issues an email code', () => {
    const state = buildRegisterFlowState({
      registrationToken,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })

    expect(state).toMatchObject({
      step: 'verify',
      registrationToken,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })
    expect(state.successMessage).toContain('验证码已发送至')
  })

  it('does not enter verification from a userId without a registration token', () => {
    const state = buildRegisterFlowState({
      userId: '11111111-1111-7111-8111-111111111111',
      emailCodeIssued: true
    })

    expect(state.step).toBe('form')
    expect(state).not.toHaveProperty('userId')
  })

  it('does not enter verification before an email code is issued', () => {
    const state = buildRegisterFlowState({ registrationToken, emailCodeIssued: false })

    expect(state.step).toBe('form')
  })

  it('persists and restores the pending verification state', () => {
    const persisted = persistRegisterFlowState({
      registrationToken,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })

    expect(persisted.step).toBe('verify')
    expect(restoreRegisterFlowState()).toMatchObject({
      step: 'verify',
      registrationToken,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: ''
    })
    const stored = JSON.parse(window.localStorage.getItem('community.register.pending'))
    expect(stored).not.toHaveProperty('userId')
    expect(stored).not.toHaveProperty('debugEmailCode')
  })

  it('clears persisted verification state when returning to the form step', () => {
    persistRegisterFlowState({
      registrationToken,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com'
    })

    clearRegisterFlowState()

    expect(restoreRegisterFlowState()).toMatchObject({
      step: 'form',
      registrationToken: '',
      emailCodeIssued: false
    })
  })

  it('removes a debug code persisted by an older frontend version', () => {
    window.localStorage.setItem('community.register.pending', JSON.stringify({
      registrationToken,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '654321'
    }))

    expect(restoreRegisterFlowState()).toMatchObject({
      step: 'verify',
      registrationToken,
      debugEmailCode: ''
    })
    expect(JSON.parse(window.localStorage.getItem('community.register.pending')))
      .not.toHaveProperty('debugEmailCode')
  })

  it('marks terminal Axios register flow errors as reset-flow failures', () => {
    expect(resolveRegisterFlowError({
      response: { data: { code: 10013, message: '注册上下文已失效，请重新注册' } }
    })).toEqual({
      resetFlow: true,
      message: '注册上下文已失效，请重新注册'
    })
    expect(resolveRegisterFlowError({
      response: { data: { code: 10014, message: '注册已完成，请直接登录' } }
    })).toEqual({
      resetFlow: true,
      message: '注册已完成，请直接登录'
    })
    expect(resolveRegisterFlowError({
      response: { data: { code: 10002, message: '账号未激活或被禁用' } }
    })).toEqual({
      resetFlow: false,
      message: '账号未激活或被禁用'
    })
    expect(resolveRegisterFlowError({
      response: { data: { code: 11001, message: '用户不存在' } }
    })).toEqual({
      resetFlow: false,
      message: '用户不存在'
    })
    expect(resolveRegisterFlowError({
      response: { data: { code: 10009, message: '注册验证码不正确' } }
    })).toEqual({
      resetFlow: false,
      message: '注册验证码不正确'
    })
  })
})
