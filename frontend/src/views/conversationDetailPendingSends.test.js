import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPendingSendTimers, PENDING_SEND_TIMEOUT_MS } from './conversationDetailPendingSends'

describe('createPendingSendTimers', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('calls onTimeout for an unresolved pending send after the fallback deadline', () => {
    const onTimeout = vi.fn()
    const timers = createPendingSendTimers({ onTimeout })

    timers.arm('client-msg-1')
    vi.advanceTimersByTime(PENDING_SEND_TIMEOUT_MS - 1)
    expect(onTimeout).not.toHaveBeenCalled()

    vi.advanceTimersByTime(1)
    expect(onTimeout).toHaveBeenCalledTimes(1)
    expect(onTimeout).toHaveBeenCalledWith('client-msg-1')
  })

  it('disarms the timer when the committed or reject receipt lands before the deadline', () => {
    const onTimeout = vi.fn()
    const timers = createPendingSendTimers({ onTimeout })

    timers.arm('client-msg-2')
    timers.disarm('client-msg-2')
    vi.advanceTimersByTime(PENDING_SEND_TIMEOUT_MS)

    expect(onTimeout).not.toHaveBeenCalled()
  })

  it('disarms only the receipt-matched timer, leaving sibling sends armed', () => {
    const onTimeout = vi.fn()
    const timers = createPendingSendTimers({ onTimeout })

    timers.arm('client-msg-a')
    timers.arm('client-msg-b')
    timers.disarm('client-msg-a')
    vi.advanceTimersByTime(PENDING_SEND_TIMEOUT_MS)

    expect(onTimeout).toHaveBeenCalledTimes(1)
    expect(onTimeout).toHaveBeenCalledWith('client-msg-b')
  })

  it('disarmAll clears every timer for conversation switches and unmount', () => {
    const onTimeout = vi.fn()
    const timers = createPendingSendTimers({ onTimeout })

    timers.arm('client-msg-1')
    timers.arm('client-msg-2')
    timers.disarmAll()
    vi.advanceTimersByTime(PENDING_SEND_TIMEOUT_MS * 2)

    expect(onTimeout).not.toHaveBeenCalled()
  })

  it('re-arming the same clientMsgId resets the deadline instead of stacking timers', () => {
    const onTimeout = vi.fn()
    const timers = createPendingSendTimers({ onTimeout })

    timers.arm('client-msg-retry')
    vi.advanceTimersByTime(PENDING_SEND_TIMEOUT_MS / 2)
    timers.arm('client-msg-retry')
    vi.advanceTimersByTime(PENDING_SEND_TIMEOUT_MS)

    // 重新 arm 之后整体时限重置：半程后再走满一个完整时限才会触发。
    expect(onTimeout).toHaveBeenCalledTimes(1)
    expect(onTimeout).toHaveBeenCalledWith('client-msg-retry')
  })

  it('a disarmed or fired timer is a no-op to disarm again', () => {
    const onTimeout = vi.fn()
    const timers = createPendingSendTimers({ onTimeout })

    timers.disarm('never-armed')
    timers.arm('client-msg-1')
    vi.advanceTimersByTime(PENDING_SEND_TIMEOUT_MS)
    timers.disarm('client-msg-1')
    expect(onTimeout).toHaveBeenCalledTimes(1)
  })
})
