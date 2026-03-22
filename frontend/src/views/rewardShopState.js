function asNumber(value, fallback = 0) {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

function orderStatusLabel(status) {
  if (status === 'PENDING') return '待处理'
  if (status === 'PROCESSING') return '处理中'
  if (status === 'FULFILLED') return '已发放'
  if (status === 'REFUNDED') return '已退款'
  if (status === 'CANCELLED') return '已取消'
  return '未知状态'
}

function fulfillmentLabel(mode) {
  return mode === 'MANUAL' ? '人工发放' : '自动发放'
}

export function buildRewardShopState({ rewardBalance = 0, items, orders } = {}) {
  const balance = Math.max(0, asNumber(rewardBalance))
  const safeItems = Array.isArray(items) ? items : []
  const safeOrders = Array.isArray(orders) ? orders : []

  return {
    rewardBalance: balance,
    items: safeItems.map((item) => {
      const stock = Math.max(0, asNumber(item?.stock))
      const costBalance = Math.max(0, asNumber(item?.costBalance))
      const soldOut = stock <= 0 || item?.status !== 'ACTIVE'
      const insufficientBalance = !soldOut && balance < costBalance
      return {
        ...item,
        stock,
        costBalance,
        soldOut,
        insufficientBalance,
        canRedeem: !soldOut && !insufficientBalance,
        fulfillmentLabel: fulfillmentLabel(item?.fulfillmentMode)
      }
    }),
    orders: safeOrders.map((order) => ({
      ...order,
      costBalanceSnapshot: Math.max(0, asNumber(order?.costBalanceSnapshot)),
      statusLabel: orderStatusLabel(order?.status),
      fulfillmentLabel: fulfillmentLabel(order?.fulfillmentModeSnapshot)
    }))
  }
}
