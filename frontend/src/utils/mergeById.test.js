import { describe, expect, it } from 'vitest'
import { mergeAppendedById, mergePrependedById } from './mergeById'

describe('mergeAppendedById', () => {
  it('appends fresh items whose id was not seen before, preserving order', () => {
    const existing = [{ id: 'a' }, { id: 'b' }]
    const fresh = [{ id: 'c' }, { id: 'd' }]

    expect(mergeAppendedById(existing, fresh).map((item) => item.id)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('drops fresh items already loaded by an earlier page when the list shifts between pages', () => {
    const existing = [{ id: 'a' }, { id: 'b' }, { id: 'c' }]
    const shifted = [{ id: 'c' }, { id: 'd' }]

    expect(mergeAppendedById(existing, shifted).map((item) => item.id)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('drops duplicate ids inside the fresh page itself', () => {
    const merged = mergeAppendedById([], [{ id: 'a' }, { id: 'a' }, { id: 'b' }])

    expect(merged.map((item) => item.id)).toEqual(['a', 'b'])
  })

  it('drops fresh items without a usable id and never mutates the inputs', () => {
    const existing = [{ id: 'a' }]
    const fresh = [{ id: '' }, { id: null }, { id: '  ' }, { id: 'b' }]

    const merged = mergeAppendedById(existing, fresh)

    expect(merged.map((item) => item.id)).toEqual(['a', 'b'])
    expect(existing).toEqual([{ id: 'a' }])
    expect(fresh).toHaveLength(4)
  })

  it('normalizes ids before comparing so numeric and blank-ish values cannot collide', () => {
    const existing = [{ id: 7 }, { id: '0' }]
    const fresh = [{ id: '7' }, { id: '0' }, { id: '8' }]

    expect(mergeAppendedById(existing, fresh).map((item) => item.id)).toEqual([7, '0', '8'])
  })

  it('supports a custom id accessor for lists keyed by another field', () => {
    const existing = [{ postId: 'p-1' }, { postId: 'p-2' }]
    const fresh = [{ postId: 'p-2' }, { postId: 'p-3' }]
    const byPostId = (item) => item?.postId

    expect(mergeAppendedById(existing, fresh, byPostId).map((item) => item.postId))
      .toEqual(['p-1', 'p-2', 'p-3'])
  })

  it('tolerates non-array inputs as empty lists', () => {
    expect(mergeAppendedById(null, [{ id: 'a' }])).toEqual([{ id: 'a' }])
    expect(mergeAppendedById([{ id: 'a' }], undefined)).toEqual([{ id: 'a' }])
  })
})

describe('mergePrependedById', () => {
  it('puts the fresh page at the head and removes colliding entries from the existing list', () => {
    const existing = [{ id: 'a' }, { id: 'b' }, { id: 'c' }]
    const fresh = [{ id: 'b' }, { id: 'd' }]

    expect(mergePrependedById(existing, fresh).map((item) => item.id)).toEqual(['b', 'd', 'a', 'c'])
  })

  it('keeps the freshly re-read version of an item instead of the stale loaded copy', () => {
    const existing = [{ id: 'a', title: 'stale' }]
    const fresh = [{ id: 'a', title: 'fresh' }]

    expect(mergePrependedById(existing, fresh)).toEqual([{ id: 'a', title: 'fresh' }])
  })

  it('supports a custom id accessor', () => {
    const existing = [{ targetId: 'u-1' }, { targetId: 'u-2' }]
    const fresh = [{ targetId: 'u-2' }]

    expect(mergePrependedById(existing, fresh, (item) => item?.targetId).map((item) => item.targetId))
      .toEqual(['u-2', 'u-1'])
  })
})
