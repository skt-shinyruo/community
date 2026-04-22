import { describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useTaxonomyStore } from './taxonomy'

describe('stores/taxonomy', () => {
  it('indexes categories by UUID string ids', () => {
    setActivePinia(createPinia())
    const store = useTaxonomyStore()
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    store.categories = [
      { id: categoryId, name: '公告' }
    ]

    expect(store.categoriesById.get(categoryId)).toEqual({
      id: categoryId,
      name: '公告'
    })
  })
})
