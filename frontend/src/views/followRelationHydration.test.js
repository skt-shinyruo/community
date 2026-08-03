import { beforeEach, describe, expect, it, vi } from 'vitest'

const { batchUserSummary, getFollowStatuses } = vi.hoisted(() => ({
  batchUserSummary: vi.fn(),
  getFollowStatuses: vi.fn()
}))

vi.mock('../api/services/userService', () => ({ batchUserSummary }))
vi.mock('../api/services/socialService', () => ({ getFollowStatuses }))

import { hydrateFollowRelations } from './followRelationHydration'

describe('followRelationHydration', () => {
  const viewerId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
  const targetA = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
  const targetB = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'

  beforeEach(() => {
    vi.clearAllMocks()
    batchUserSummary.mockResolvedValue({
      data: [
        { id: targetA, username: 'Alice', headerUrl: '/alice.png' },
        { id: targetB, username: 'Bob', headerUrl: '/bob.png' }
      ]
    })
    getFollowStatuses.mockResolvedValue({
      data: { [targetA]: true, [targetB]: false }
    })
  })

  it('hydrates one page with one user batch and one follow-status batch', async () => {
    const relations = [
      { targetId: targetA, followTime: '2026-08-01T00:00:00Z' },
      { targetId: targetB, followTime: '2026-08-02T00:00:00Z' }
    ]

    const hydrated = await hydrateFollowRelations(relations, {
      authed: true,
      viewerUserId: viewerId
    })

    expect(batchUserSummary).toHaveBeenCalledTimes(1)
    expect(batchUserSummary).toHaveBeenCalledWith([targetA, targetB])
    expect(getFollowStatuses).toHaveBeenCalledTimes(1)
    expect(getFollowStatuses).toHaveBeenCalledWith(3, [targetA, targetB])
    expect(hydrated).toEqual([
      { ...relations[0], user: expect.objectContaining({ username: 'Alice' }), hasFollowed: true },
      { ...relations[1], user: expect.objectContaining({ username: 'Bob' }), hasFollowed: false }
    ])
  })

  it('does not query private follow statuses for an anonymous viewer', async () => {
    await hydrateFollowRelations([{ targetId: targetA }], { authed: false })

    expect(batchUserSummary).toHaveBeenCalledWith([targetA])
    expect(getFollowStatuses).not.toHaveBeenCalled()
  })
})
