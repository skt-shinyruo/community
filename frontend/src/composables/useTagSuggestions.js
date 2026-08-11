import { onScopeDispose, ref, unref, watch } from 'vue'

export function useTagSuggestions({
  query,
  hotTags,
  suggest,
  limit = 8,
  debounceMs = 180
} = {}) {
  const suggestions = ref([])
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
