import { onScopeDispose, ref, unref, watch } from 'vue'

/**
 * 标签建议组合式函数的配置。
 * @typedef {object} TagSuggestionOptions
 * @property {import('vue').Ref<string> | string} [query] 搜索关键字（支持响应式引用）
 * @property {unknown} [hotTags] 关键字为空时的热门标签回退列表
 * @property {(params: { q: string, limit: number }) => PromiseLike<{ data?: unknown }>} suggest 标签建议查询函数
 * @property {number} [limit] 建议数量上限
 * @property {number} [debounceMs] 输入防抖毫秒数
 */

/**
 * @param {TagSuggestionOptions} [options]
 */
export function useTagSuggestions({
  query,
  hotTags,
  suggest,
  limit = 8,
  debounceMs = 180
} = /** @type {TagSuggestionOptions} */ ({})) {
  const suggestions = ref(/** @type {{ name?: unknown }[]} */ ([]))
  let revision = 0
  let timer = null

  const stop = watch(
    [
      () => String(unref(query) || '').trim(),
      () => unref(hotTags)
    ],
    ([keyword, currentHotTags]) => {
      revision += 1
      const requestRevision = revision
      if (timer) globalThis.clearTimeout(timer)
      timer = null

      if (!keyword) {
        suggestions.value = (Array.isArray(currentHotTags) ? currentHotTags : []).slice(0, limit)
        return
      }

      suggestions.value = []
      timer = globalThis.setTimeout(async () => {
        timer = null
        try {
          const response = await suggest({ q: keyword, limit })
          if (requestRevision !== revision) return
          suggestions.value = Array.isArray(response?.data) ? response.data : []
        } catch {
          if (requestRevision === revision) suggestions.value = []
        }
      }, debounceMs)
    },
    { immediate: true }
  )

  const dispose = () => {
    revision += 1
    if (timer) globalThis.clearTimeout(timer)
    timer = null
    stop()
  }
  onScopeDispose(dispose)

  return { suggestions, dispose }
}
