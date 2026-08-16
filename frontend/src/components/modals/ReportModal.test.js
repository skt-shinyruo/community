// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { createReport, showToast } = vi.hoisted(() => ({
  createReport: vi.fn(),
  showToast: vi.fn()
}))

vi.mock('../../api/services/reportService', () => ({ createReport }))
vi.mock('../../ui/toastService', () => ({ showToast }))

import { useAuthStore } from '../../stores/auth'
import ReportModal from './ReportModal.vue'

function deferred() {
  let resolve
  const promise = new Promise((res) => { resolve = res })
  return { promise, resolve }
}

describe('ReportModal identity scope', () => {
  let pinia

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()
  })

  it('does not submit old-account completion state after the session changes', async () => {
    const auth = useAuthStore()
    auth.installSession({ accessToken: 'token-a', me: { userId: 'user-a' } })
    const oldReport = deferred()
    createReport.mockReturnValueOnce(oldReport.promise)

    const wrapper = mount(ReportModal, {
      props: { targetType: 'post', targetId: 'post-1' },
      global: {
        plugins: [pinia],
        stubs: {
          UiButton: true,
          UiIconButton: true
        }
      }
    })

    const pending = wrapper.vm.submit()
    auth.installSession({ accessToken: 'token-b', me: { userId: 'user-b' } })
    await flushPromises()
    oldReport.resolve({})
    await pending

    expect(showToast).not.toHaveBeenCalled()
    expect(wrapper.emitted('submitted')).toBeUndefined()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
