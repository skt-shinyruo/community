// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import StepUpSecurityModal from './StepUpSecurityModal.vue'

describe('StepUpSecurityModal', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  /**
   * @param {Record<string, unknown>} props
   */
  function mountModal(props = {}) {
    return mount(StepUpSecurityModal, {
      props,
      global: {
        stubs: {
          RouterLink: true
        }
      }
    })
  }

  it('renders hardware mode by default and emits cancel on close', async () => {
    const wrapper = mountModal({
      title: '敏感操作确认',
      actionName: '账户资金提取'
    })

    expect(wrapper.text()).toContain('敏感操作确认')
    expect(wrapper.text()).toContain('账户资金提取')
    expect(wrapper.text()).toContain('FIDO2 / WebAuthn 硬件保护模式')
    expect(wrapper.find('[data-test="stepup-touch-btn"]').exists()).toBe(true)

    // Trigger cancel
    const cancelBtn = wrapper.findAll('button').find((b) => b.text() === '取消')
    expect(cancelBtn).toBeDefined()
    if (!cancelBtn) throw new Error('cancelBtn missing')
    await cancelBtn.trigger('click')

    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('simulates hardware verification and emits verified event', async () => {
    const wrapper = mountModal({
      actionName: '转账操作'
    })

    const touchBtn = wrapper.get('[data-test="stepup-touch-btn"]')
    await touchBtn.trigger('click')
    await flushPromises()

    const verifiedEvents = wrapper.emitted('verified')
    expect(verifiedEvents).toBeDefined()
    if (!verifiedEvents) throw new Error('未发出 verified')
    const payload = /** @type {Record<string, unknown>} */ (verifiedEvents[0][0])
    expect(payload.type).toBe('webauthn')
    expect(typeof payload.timestamp).toBe('number')
  })

  it('handles WebAuthn cancellation gracefully', async () => {
    const originalCredentials = navigator.credentials
    const notAllowedError = new Error('User cancelled')
    notAllowedError.name = 'NotAllowedError'

    vi.stubGlobal('PublicKeyCredential', class {})
    Object.defineProperty(navigator, 'credentials', {
      value: {
        get: vi.fn().mockRejectedValue(notAllowedError)
      },
      configurable: true,
      writable: true
    })

    const wrapper = mountModal({
      actionName: '大额划转'
    })

    const touchBtn = wrapper.get('[data-test="stepup-touch-btn"]')
    await touchBtn.trigger('click')
    await flushPromises()

    expect(wrapper.emitted('verified')).toBeUndefined()
    expect(wrapper.text()).toContain('安全密钥验证已取消或超时，请重试或使用备用码。')

    if (originalCredentials) {
      Object.defineProperty(navigator, 'credentials', {
        value: originalCredentials,
        configurable: true,
        writable: true
      })
    }
  })

  it('supports switching to backup code mode and validating codes', async () => {
    const wrapper = mountModal({
      actionName: '安全设置变更'
    })

    // Click toggle to backup mode
    const toggleBtn = wrapper.findAll('button').find((b) => b.text() === '使用备用恢复码')
    expect(toggleBtn).toBeDefined()
    if (!toggleBtn) throw new Error('toggleBtn missing')
    await toggleBtn.trigger('click')

    expect(wrapper.text()).toContain('无法使用硬件安全密钥？')
    expect(wrapper.find('[data-test="stepup-submit-backup"]').exists()).toBe(true)

    // Try submit without code -> button is disabled
    const submitBtn = wrapper.get('[data-test="stepup-submit-backup"]')
    expect(submitBtn.attributes('disabled')).toBeDefined()

    // Type a code that is too short
    const input = wrapper.get('input')
    await input.setValue('123')
    await submitBtn.trigger('click')
    expect(wrapper.text()).toContain('备用恢复码格式不正确')
    expect(wrapper.emitted('verified')).toBeUndefined()

    // Enter a valid backup code
    await input.setValue('RECOV-987654')
    await submitBtn.trigger('click')

    // Advance timer for simulate async verification
    vi.advanceTimersByTime(150)
    await flushPromises()

    const verifiedEvents = wrapper.emitted('verified')
    expect(verifiedEvents).toBeDefined()
    if (!verifiedEvents) throw new Error('未发出 verified')
    const payload = /** @type {Record<string, unknown>} */ (verifiedEvents[0][0])
    expect(payload).toMatchObject({
      type: 'backup_code',
      code: 'RECOV-987654'
    })
    expect(typeof payload.timestamp).toBe('number')

    // Toggle back to hardware mode
    const switchBackBtn = wrapper.findAll('button').find((b) => b.text() === '返回安全密钥验证')
    expect(switchBackBtn).toBeDefined()
    if (!switchBackBtn) throw new Error('switchBackBtn missing')
    await switchBackBtn.trigger('click')

    expect(wrapper.find('[data-test="stepup-touch-btn"]').exists()).toBe(true)
  })
})
