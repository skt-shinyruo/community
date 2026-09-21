// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getUserProfile, listBlockedUsers, blockUser, unblockUser, showToast, showErrorToast } = vi.hoisted(() => ({
  getUserProfile: vi.fn(),
  listBlockedUsers: vi.fn(),
  blockUser: vi.fn(),
  unblockUser: vi.fn(),
  showToast: vi.fn(),
  showErrorToast: vi.fn()
}))

vi.mock('../../api/services/userService', () => ({ getUserProfile }))
vi.mock('../../api/services/blockService', () => ({ listBlockedUsers, blockUser, unblockUser }))
vi.mock('../../ui/toastService', () => ({ showToast, showErrorToast, setToastHandler: vi.fn() }))

import UiUserCard from './UiUserCard.vue'
import { useAuthStore } from '../../stores/auth'

const VIEWER_ID = '99999999-9999-7999-8999-999999999999'

function mountCard(user, { authed = false } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (authed) {
    useAuthStore().installSession({
      accessToken: 'token-a',
      me: { userId: VIEWER_ID, username: 'viewer' }
    })
  }

  return mount(UiUserCard, {
    props: { user },
    slots: { default: '<span>用户</span>' },
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a data-test="profile-link" :data-user-id="to.params.userId"><slot /></a>'
        },
        UiAvatar: true,
        UiRoleBadge: true,
        ReportModal: true
      }
    }
  })
}

describe('UiUserCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getUserProfile.mockResolvedValue(null)
    listBlockedUsers.mockResolvedValue({ data: [] })
    blockUser.mockResolvedValue({})
    unblockUser.mockResolvedValue({})
  })

  it('uses only the canonical id field for the profile link', async () => {
    const legacyCard = mountCard({
      userId: '11111111-1111-7111-8111-111111111111',
      username: 'legacy',
      createTime: '2026-01-01T00:00:00Z',
      likeCount: 0
    })

    await legacyCard.get('.user-card-wrapper').trigger('mouseenter')
    expect(legacyCard.find('[data-test="profile-link"]').exists()).toBe(false)

    const canonicalCard = mountCard({
      id: '22222222-2222-7222-8222-222222222222',
      username: 'canonical',
      createTime: '2026-01-01T00:00:00Z',
      likeCount: 0
    })

    await canonicalCard.get('.user-card-wrapper').trigger('mouseenter')
    expect(canonicalCard.get('[data-test="profile-link"]').attributes('data-user-id'))
      .toBe('22222222-2222-7222-8222-222222222222')
  })

  it('does not apply a late profile response to a replaced user card', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolveOld
    getUserProfile.mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
    if (!resolveOld) throw new Error('未捕获 deferred resolve')
    const wrapper = mountCard({ id: 'user-a', username: 'A' })

    await wrapper.get('.user-card-wrapper').trigger('mouseenter')
    await wrapper.setProps({ user: { id: 'user-b', username: 'B' } })
    resolveOld({ id: 'user-a', username: 'late A', createTime: '2026-01-01', likeCount: 1 })
    await flushPromises()

    expect(wrapper.text()).not.toContain('late A')
  })

  it('shows a single success toast when the block succeeds but the blocklist resync fails', async () => {
    const target = { id: '33333333-3333-7333-8333-333333333333', username: 'target', createTime: '2026-01-01', likeCount: 0 }
    const wrapper = mountCard(target, { authed: true })
    await wrapper.get('.user-card-wrapper').trigger('mouseenter')
    await flushPromises()

    // 写操作成功、读侧屏蔽列表重同步失败：只出现成功 toast，不再叠加「操作失败」。
    listBlockedUsers.mockRejectedValueOnce(new Error('blocklist unavailable'))
    const blockButton = wrapper.findAll('button').find((button) => button.text() === '屏蔽')
    if (!blockButton) throw new Error('未找到屏蔽按钮')
    await blockButton.trigger('click')
    await flushPromises()

    expect(blockUser).toHaveBeenCalledWith('33333333-3333-7333-8333-333333333333')
    expect(showToast).toHaveBeenCalledTimes(1)
    expect(showToast).toHaveBeenCalledWith({ type: 'success', text: '已屏蔽该用户' })
    expect(showErrorToast).not.toHaveBeenCalled()
    // 重同步走静默通道，不触发全局错误 toast。
    expect(listBlockedUsers.mock.calls.at(-1)).toEqual([{ silent: true }])
  })

  it('shows a single error toast when the block itself fails', async () => {
    const target = { id: '33333333-3333-7333-8333-333333333333', username: 'target', createTime: '2026-01-01', likeCount: 0 }
    const wrapper = mountCard(target, { authed: true })
    await wrapper.get('.user-card-wrapper').trigger('mouseenter')
    await flushPromises()

    blockUser.mockRejectedValueOnce(new Error('server rejected'))
    const blockButton = wrapper.findAll('button').find((button) => button.text() === '屏蔽')
    if (!blockButton) throw new Error('未找到屏蔽按钮')
    await blockButton.trigger('click')
    await flushPromises()

    expect(showToast).not.toHaveBeenCalled()
    expect(showErrorToast).toHaveBeenCalledTimes(1)
    expect(showErrorToast).toHaveBeenCalledWith(expect.any(Error), expect.objectContaining({ title: '操作失败' }))
  })
})
