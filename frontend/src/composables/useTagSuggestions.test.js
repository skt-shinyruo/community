import { effectScope, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useTagSuggestions } from './useTagSuggestions'

function deferred() {
  /** @type {{ resolve?: (value: unknown) => void }} */
  const handle = {}
  const promise = new Promise((done) => { handle.resolve = done })
  return /** @type {{ promise: Promise<unknown>, resolve: (value: unknown) => void }} */ ({ ...handle, promise })
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
    /** @type {import('vitest').Mock<(params: { q: string, limit: number }) => PromiseLike<{ data?: unknown }>>} */
    const suggest = vi.fn((/** @type {{ q: string, limit: number }} */ _params) => /** @type {PromiseLike<{ data?: unknown }>} */ (pending.promise))
    const scope = effectScope()
    const state = scope.run(() => useTagSuggestions({ query, hotTags, suggest, debounceMs: 10 }))
    // scope.run 可能返回 undefined：缺 state 说明组合式初始化失败，直接中断用例。
    if (!state) throw new Error('useTagSuggestions 未初始化')

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
    const hotTags = ref(/** @type {{ name: string }[]} */ ([]))
    const scope = effectScope()
    const state = scope.run(() => useTagSuggestions({ query, hotTags, suggest: vi.fn() }))
    // 同上：scope.run 的返回可能为 undefined。
    if (!state) throw new Error('useTagSuggestions 未初始化')

    hotTags.value = [{ name: '新热词' }]
    await nextTick()

    expect(state.suggestions.value).toEqual([{ name: '新热词' }])
    scope.stop()
  })
})
