import { computed, onBeforeUnmount, ref, toValue, watch } from 'vue'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { buildMarketState, mergeMarketPage } from '../marketState'

// side 是买入/卖出域内 tab 的来源：同一组件实例被两条路由复用时，side 变化并入
// sessionScope，触发既有 reset + reload，并让在途响应按旧 scope 一并丢弃（buying/selling
// 状态互不串扰）。listOrders 由调用方以闭包按当前 side 分派；initialError/moreError
// 支持 ref/getter，错误文案跟随当前 side 的策略。
export function useMarketOrderList({
  auth,
  listOrders,
  initialError = '加载订单失败',
  moreError = '加载更多订单失败',
  pageSize = 20,
  side
} = {}) {
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref('')
  const pageError = ref('')
  const orders = ref([])
  const page = ref(0)
  const hasNext = ref(false)
  let requestGeneration = 0

  const state = computed(() => buildMarketState({ orders: orders.value }))
  const sessionScope = computed(() => [
    toValue(side) || '',
    auth.tokenGeneration,
    normalizeOpaqueId(auth.userId),
    auth.authed ? 'authenticated' : 'anonymous'
  ].join(':'))

  const isCurrentRequest = (generation, scope) =>
    generation === requestGeneration && scope === sessionScope.value

  async function requestPage(targetPage, append) {
    const generation = ++requestGeneration
    const scope = sessionScope.value
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
      if (!isCurrentRequest(generation, scope)) return
      const data = Array.isArray(response?.data) ? response.data : []
      orders.value = append ? mergeMarketPage(orders.value, data, 'orderId') : data
      page.value = Number(response?.page ?? targetPage)
      hasNext.value = response?.hasNext === true
    } catch (cause) {
      if (!isCurrentRequest(generation, scope)) return
      if (append) pageError.value = cause?.message || toValue(moreError)
      else error.value = cause?.message || toValue(initialError)
    } finally {
      if (isCurrentRequest(generation, scope)) {
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
    requestGeneration += 1
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
    requestGeneration += 1
  })

  return { state, loading, loadingMore, error, pageError, hasNext, reload, loadMore, reset }
}
