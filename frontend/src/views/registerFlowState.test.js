import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildRegisterFlowState, clearRegisterFlowState, persistRegisterFlowState, resolveRegisterFlowError, restoreRegisterFlowState } from './registerFlowState'

describe('registerFlowState', () => {
  const userId = '11111111-1111-7111-8111-111111111111'

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
      userId: '',
      emailCodeIssued: false,
      maskedEmail: '',
      debugEmailCode: '',
      successMessage: ''
    })
  })

  it('switches to the verify step when registration issues an email code', () => {
    const state = buildRegisterFlowState({
      userId,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })

    expect(state).toMatchObject({
      step: 'verify',
      userId,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })
    expect(state.successMessage).toContain('验证码已发送至')
  })

  it('persists and restores the pending verification state', () => {
    const persisted = persistRegisterFlowState({
      userId,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })

    expect(persisted.step).toBe('verify')
    expect(restoreRegisterFlowState()).toMatchObject({
      step: 'verify',
      userId,
      maskedEmail: 'a***e@example.com',
      debugEmailCode: '123456'
    })
  })

  it('clears persisted verification state when returning to the form step', () => {
    persistRegisterFlowState({
      userId,
      emailCodeIssued: true,
      maskedEmail: 'a***e@example.com'
    })

    clearRegisterFlowState()

    expect(restoreRegisterFlowState()).toMatchObject({
      step: 'form',
      userId: '',
      emailCodeIssued: false
    })
  })

  it('marks already-activated and missing-user errors as terminal register flow failures', () => {
    expect(resolveRegisterFlowError({ code: 10002, message: '账号已激活，请直接登录' })).toEqual({
      resetFlow: true,
      message: '账号已完成验证，请直接登录'
    })
    expect(resolveRegisterFlowError({ code: 11001, message: '用户不存在' })).toEqual({
      resetFlow: true,
      message: '注册上下文已失效，请重新注册'
    })
    expect(resolveRegisterFlowError({ code: 10009, message: '注册验证码不正确' })).toEqual({
      resetFlow: false,
      message: '注册验证码不正确'
    })
  })
})
