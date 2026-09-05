import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { buildMarketState, mergeMarketPage } from '../marketState'

export function useMarketOrderList({
  auth,
  listOrders,
  initialError = '加载订单失败',
  moreError = '加载更多订单失败',
  pageSize = 20
} = {}) {
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const pageError = ref('')
  const orders = ref([])
  const page = ref(0)
  const hasNext = ref(false)

  const state = computed(() => buildMarketState({ orders: orders.value }))
  const sessionScope = computed(() => [
    auth.tokenGeneration,
    normalizeOpaqueId(auth.userId),
    auth.authed ? 'authenticated' : 'anonymous'
  ].join(':'))

  const requestTracker = createLatestRequestTracker({ getScope: () => sessionScope.value })

  async function requestPage(targetPage, append) {
    const requestHandle = requestTracker.begin()
    if (append) {
      loadingMore.value = true
      pageError.value = ''
    } else {
      loading.value = true
      loadingMore.value = false
      error.value = ''
      pageError.value = ''
    }

    try {
      const response = await listOrders({ page: targetPage, size: pageSize })
      if (!requestTracker.isCurrent(requestHandle)) return
      const data = Array.isArray(response?.data) ? response.data : []
      orders.value = append ? mergeMarketPage(orders.value, data, 'orderId') : data
      page.value = Number(response?.page ?? targetPage)
      hasNext.value = response?.hasNext === true
    } catch (cause) {
      if (!requestTracker.isCurrent(requestHandle)) return
      if (append) pageError.value = cause?.message || moreError
      else error.value = cause?.message || initialError
    } finally {
      if (requestTracker.isCurrent(requestHandle)) {
        if (append) loadingMore.value = false
        else loading.value = false
      }
    }
  }

  const reload = () => requestPage(0, false)
  const loadMore = () => {
    if (loading.value || loadingMore.value || !hasNext.value) return Promise.resolve()
    return requestPage(page.value + 1, true)
  }

  function reset() {
    requestTracker.invalidate()
    orders.value = []
    page.value = 0
    hasNext.value = false
    loading.value = false
    loadingMore.value = false
    error.value = ''
    pageError.value = ''
  }

  watch(sessionScope, () => {
    reset()
    if (auth.authed) void reload()
  }, { immediate: true })

  onBeforeUnmount(() => {
    requestTracker.invalidate()
  })

  return { state, loading, loadingMore, error, pageError, hasNext, reload, loadMore, reset }
}
