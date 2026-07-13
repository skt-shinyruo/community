// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'

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
})
