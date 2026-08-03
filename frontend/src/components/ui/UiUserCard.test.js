// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getUserProfile, listBlockedUsers, blockUser, unblockUser } = vi.hoisted(() => ({
  getUserProfile: vi.fn(),
  listBlockedUsers: vi.fn(),
  blockUser: vi.fn(),
  unblockUser: vi.fn()
}))

vi.mock('../../api/services/userService', () => ({ getUserProfile }))
vi.mock('../../api/services/blockService', () => ({ listBlockedUsers, blockUser, unblockUser }))

import UiUserCard from './UiUserCard.vue'

function mountCard(user) {
  const pinia = createPinia()
  setActivePinia(pinia)

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
    let resolveOld
    getUserProfile.mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
    const wrapper = mountCard({ id: 'user-a', username: 'A' })

    await wrapper.get('.user-card-wrapper').trigger('mouseenter')
    await wrapper.setProps({ user: { id: 'user-b', username: 'B' } })
    resolveOld({ id: 'user-a', username: 'late A', createTime: '2026-01-01', likeCount: 1 })
    await flushPromises()

    expect(wrapper.text()).not.toContain('late A')
  })
})
