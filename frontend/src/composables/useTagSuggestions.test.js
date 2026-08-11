import { effectScope, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useTagSuggestions } from './useTagSuggestions'

function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

describe('useTagSuggestions', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps the hot-tag fallback when an older suggestion request finishes late', async () => {
    vi.useFakeTimers()
    const query = ref('')
    const hotTags = ref([{ name: '热门' }])
    const pending = deferred()
    const suggest = vi.fn(() => pending.promise)
    const scope = effectScope()
    const state = scope.run(() => useTagSuggestions({ query, hotTags, suggest, debounceMs: 10 }))

    expect(state.suggestions.value).toEqual([{ name: '热门' }])
    query.value = 'vue'
    await nextTick()
    await vi.advanceTimersByTimeAsync(10)
    expect(suggest).toHaveBeenCalledWith({ q: 'vue', limit: 8 })

    query.value = ''
    await nextTick()
    expect(state.suggestions.value).toEqual([{ name: '热门' }])

    pending.resolve({ data: [{ name: '过期结果' }] })
    await Promise.resolve()
    expect(state.suggestions.value).toEqual([{ name: '热门' }])
    scope.stop()
  })

  it('tracks hot-tag changes while the query is empty', async () => {
    const query = ref('')
    const hotTags = ref([])
    const scope = effectScope()
    const state = scope.run(() => useTagSuggestions({ query, hotTags, suggest: vi.fn() }))

    hotTags.value = [{ name: '新热词' }]
    await nextTick()

    expect(state.suggestions.value).toEqual([{ name: '新热词' }])
    scope.stop()
  })
})
