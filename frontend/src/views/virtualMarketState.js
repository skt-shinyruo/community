function asNumber(value, fallback = 0) {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

function normalizeStatus(status, fallback = 'UNKNOWN') {
  const normalized = String(status || '').trim().toUpperCase()
  return normalized || fallback
}

function deliveryLabel(mode) {
  const normalized = String(mode || '').trim().toUpperCase()
  if (normalized === 'PRELOADED') return '自动交付'
  if (normalized === 'MANUAL') return '卖家手工交付'
  return '待配置'
}

function listingStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ACTIVE') return '在售'
  if (normalized === 'SOLD_OUT') return '已售罄'
  if (normalized === 'PAUSED') return '已暂停'
  if (normalized === 'CLOSED') return '已关闭'
  return '待处理'
}

function orderStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ESCROWED') return '已托管'
  if (normalized === 'DELIVERED') return '待确认'
  if (normalized === 'COMPLETED') return '已完成'
  if (normalized === 'CANCELLED') return '已取消'
  if (normalized === 'DISPUTED') return '申诉中'
  if (normalized === 'REFUNDED') return '已退款'
  return '处理中'
}

function disputeStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'OPEN') return '待卖家处理'
  if (normalized === 'SELLER_ACCEPTED') return '卖家已同意'
  if (normalized === 'SELLER_REJECTED') return '待管理员裁定'
  if (normalized === 'ADMIN_RESOLVED') return '管理员已裁定'
  return '处理中'
}

function amountText(amount) {
  const normalized = asNumber(amount)
  return `${normalized} 积分`
}

function stockText(stockAvailable) {
  const normalized = asNumber(stockAvailable)
  if (normalized <= 0) return '库存紧张'
  return `剩余 ${normalized}`
}

export function buildVirtualMarketState({ listings, orders, disputes } = {}) {
  const safeListings = Array.isArray(listings) ? listings : []
  const safeOrders = Array.isArray(orders) ? orders : []
  const safeDisputes = Array.isArray(disputes) ? disputes : []

  return {
    listings: safeListings.map((item, index) => {
      const listingId = item?.listingId ?? item?.id ?? index + 1
      const unitPrice = asNumber(item?.unitPrice)
      return {
        ...item,
        listingId,
        deliveryLabel: deliveryLabel(item?.deliveryMode),
        statusLabel: listingStatusLabel(item?.status),
        unitPriceText: amountText(unitPrice),
        stockText: stockText(item?.stockAvailable)
      }
    }),
    orders: safeOrders.map((item, index) => {
      const orderId = item?.orderId ?? index + 1
      const totalAmount = asNumber(item?.totalAmount)
      return {
        ...item,
        orderId,
        statusLabel: orderStatusLabel(item?.status),
        totalAmountText: amountText(totalAmount),
        autoConfirmText: item?.autoConfirmAt ? `自动确认 ${item.autoConfirmAt}` : '等待下一步动作'
      }
    }),
    disputes: safeDisputes.map((item, index) => ({
      ...item,
      disputeId: item?.disputeId ?? index + 1,
      statusLabel: disputeStatusLabel(item?.status)
    }))
  }
}
