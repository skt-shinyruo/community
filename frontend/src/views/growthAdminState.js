function asNumber(value, fallback = 0) {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

function accountRiskLabel(account) {
  return asNumber(account?.frozenBalance) > 0 ? '冻结中' : '正常'
}

function orderStatusLabel(status) {
  if (status === 'PENDING') return '待处理'
  if (status === 'PROCESSING') return '处理中'
  if (status === 'FULFILLED') return '已发放'
  if (status === 'REFUNDED') return '已退款'
  if (status === 'CANCELLED') return '已取消'
  return '未知状态'
}

function itemWarningLabel(item) {
  if (item?.status === 'INACTIVE') return '已停用'
  const highCost = asNumber(item?.costBalance) >= 100
  const lowStock = asNumber(item?.stock) <= 1
  if (highCost && lowStock) return '高成本 · 低库存'
  if (highCost) return '高成本'
  if (lowStock) return '低库存'
  return ''
}

export function buildGrowthAdminState({ accounts, items, orders } = {}) {
  const safeAccounts = Array.isArray(accounts) ? accounts : []
  const safeItems = Array.isArray(items) ? items : []
  const safeOrders = Array.isArray(orders) ? orders : []

  return {
    accounts: safeAccounts.map((account) => ({
      ...account,
      canAdjustScore: true,
      canAdjustRewardBalance: true,
      riskLabel: accountRiskLabel(account)
    })),
    items: safeItems.map((item) => ({
      ...item,
      warningLabel: itemWarningLabel(item)
    })),
    orders: safeOrders.map((order) => ({
      ...order,
      statusLabel: orderStatusLabel(order?.status),
      canFulfill: order?.status === 'PENDING',
      canCancel: order?.status === 'PENDING',
      canRefund: order?.status === 'FULFILLED'
    }))
  }
}
