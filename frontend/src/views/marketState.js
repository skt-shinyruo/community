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

function goodsTypeLabel(goodsType) {
  const normalized = String(goodsType || '').trim().toUpperCase()
  if (normalized === 'VIRTUAL') return '虚拟商品'
  if (normalized === 'PHYSICAL') return '实物商品'
  return '未知类型'
}

function shipmentLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'SHIPPED') return '已发货'
  if (normalized === 'COMPLETED') return '已收货'
  return '等待卖家发货'
}

function listingStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ACTIVE') return '在售'
  if (normalized === 'SOLD_OUT') return '已售罄'
  if (normalized === 'PAUSED') return '已暂停'
  if (normalized === 'CLOSED') return '已关闭'
  return '待处理'
}

function fulfillmentLabel(item) {
  const goodsType = String(item?.goodsType || '').trim().toUpperCase()
  if (goodsType === 'VIRTUAL') return deliveryLabel(item?.deliveryMode)
  if (goodsType === 'PHYSICAL') return '实物配送'
  return '履约待确认'
}

function trustLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ACTIVE') return '钱包托管'
  if (normalized === 'SOLD_OUT') return '交易已结束'
  if (normalized === 'PAUSED') return '暂不可购买'
  if (normalized === 'CLOSED') return '已关闭'
  return '状态待确认'
}

function orderStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ESCROWED') return '已托管'
  if (normalized === 'DELIVERED') return '待确认'
  if (normalized === 'SHIPPED') return '已发货'
  if (normalized === 'COMPLETED') return '已完成'
  if (normalized === 'CANCELLED') return '已取消'
  if (normalized === 'DISPUTED') return '申诉中'
  if (normalized === 'REFUNDED') return '已退款'
  return '处理中'
}

function fundsLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ESCROWED' || normalized === 'PAID' || normalized === 'HELD') return '托管中'
  if (normalized === 'ESCROW_PENDING' || normalized.endsWith('_PENDING')) return '处理中'
  if (normalized === 'RELEASED' || normalized === 'COMPLETED') return '已放款'
  if (normalized === 'REFUNDED') return '已退款'
  if (normalized === 'CANCELLED') return '已取消'
  return '资金状态待确认'
}

function fulfillmentStateLabel(item) {
  const normalized = normalizeStatus(item?.fulfillmentStatus || item?.shipmentStatus || item?.status)
  if (normalized === 'DELIVERED') return '已交付'
  if (normalized === 'SHIPPED') return '已发货'
  if (normalized === 'COMPLETED') return '已完成'
  if (normalized === 'DISPUTED') return '争议处理中'
  if (String(item?.goodsType || '').trim().toUpperCase() === 'VIRTUAL') return deliveryLabel(item?.deliveryMode)
  return '等待履约'
}

function nextOrderActionLabel(item) {
  const status = normalizeStatus(item?.status)
  const fulfillment = normalizeStatus(item?.fulfillmentStatus || item?.shipmentStatus)
  if (status === 'SHIPPED' || fulfillment === 'SHIPPED') return '等待买家确认收货'
  if (status === 'DELIVERED' || fulfillment === 'DELIVERED') return '等待买家确认完成'
  if (status === 'ESCROWED') return '等待卖家履约'
  if (status === 'DISPUTED') return '等待争议处理'
  if (status === 'COMPLETED') return '订单已完成'
  if (status === 'CANCELLED') return '订单已取消'
  if (status === 'REFUNDED') return '退款已完成'
  return '查看订单详情'
}

function lifecycleStepState(active, complete = false) {
  if (complete) return 'complete'
  return active ? 'active' : 'pending'
}

function buildLifecycleSteps(item) {
  const status = normalizeStatus(item?.status)
  const escrow = normalizeStatus(item?.escrowStatus || item?.fundState || item?.fundsStatus)
  const fulfillment = normalizeStatus(item?.fulfillmentStatus || item?.shipmentStatus || item?.status)
  const disputed = status === 'DISPUTED' || !!item?.disputeId || normalizeStatus(item?.disputeStatus, '') !== ''
  const complete = status === 'COMPLETED'
  const cancelled = status === 'CANCELLED' || status === 'REFUNDED'
  const escrowed = ['ESCROWED', 'PAID', 'HELD', 'RELEASED', 'COMPLETED'].includes(escrow) || ['ESCROWED', 'DELIVERED', 'SHIPPED', 'COMPLETED', 'DISPUTED'].includes(status)
  const fulfilled = ['DELIVERED', 'SHIPPED', 'COMPLETED'].includes(fulfillment) || ['DELIVERED', 'SHIPPED', 'COMPLETED'].includes(status)
  const confirmed = complete || cancelled

  return [
    { key: 'created', label: '已创建', state: 'complete' },
    { key: 'funds', label: escrowed ? '资金托管' : '等待托管', state: lifecycleStepState(escrowed, escrowed) },
    { key: 'fulfillment', label: fulfilled ? fulfillmentStateLabel(item) : '等待履约', state: lifecycleStepState(fulfilled, fulfilled) },
    { key: 'confirmation', label: confirmed ? orderStatusLabel(status) : '待确认', state: lifecycleStepState(!confirmed && fulfilled, confirmed) },
    { key: 'dispute', label: disputed ? '争议处理中' : '无争议', state: disputed ? 'active' : 'pending' }
  ]
}

function disputeStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'OPEN') return '待卖家处理'
  if (normalized === 'SELLER_ACCEPTED') return '卖家已同意'
  if (normalized === 'SELLER_REJECTED') return '待管理员裁定'
  if (normalized === 'ADMIN_RESOLVED') return '管理员已裁定'
  return '处理中'
}

function fundStateLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ESCROWED' || normalized === 'HELD') return '资金托管中'
  if (normalized === 'RELEASED') return '已放款'
  if (normalized === 'REFUNDED') return '已退款'
  if (normalized.endsWith('_PENDING')) return '资金处理中'
  return '资金状态待确认'
}

function nextDisputeActionLabel(item) {
  const normalized = normalizeStatus(item?.status)
  if (normalized === 'OPEN') return '等待卖家回应'
  if (normalized === 'SELLER_ACCEPTED') return '等待退款处理'
  if (normalized === 'SELLER_REJECTED') return '需要管理员裁定'
  if (normalized === 'ADMIN_RESOLVED') return '裁定已完成'
  return '查看争议详情'
}

function inventoryStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'AVAILABLE') return '可售'
  if (normalized === 'LOCKED' || normalized === 'RESERVED') return '已锁定'
  if (normalized === 'SOLD') return '已售出'
  if (normalized === 'INVALIDATED') return '已失效'
  return '库存状态待确认'
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

function autoConfirmText(item) {
  if (item?.autoConfirmAt) return `自动确认 ${item.autoConfirmAt}`
  if (String(item?.goodsType || '').trim().toUpperCase() === 'PHYSICAL' && normalizeStatus(item?.status) === 'SHIPPED') {
    return '等待买家收货'
  }
  return '等待下一步动作'
}

function addressLine(item) {
  const parts = [
    item?.province,
    item?.city,
    item?.district,
    item?.detailAddress
  ].map((part) => String(part || '').trim()).filter(Boolean)
  return parts.join(' ')
}

export function buildMarketState({ listings, orders, disputes, addresses, inventory } = {}) {
  const safeListings = Array.isArray(listings) ? listings : []
  const safeOrders = Array.isArray(orders) ? orders : []
  const safeDisputes = Array.isArray(disputes) ? disputes : []
  const safeAddresses = Array.isArray(addresses) ? addresses : []
  const safeInventory = Array.isArray(inventory) ? inventory : []

  return {
    listings: safeListings.map((item, index) => {
      const listingId = item?.listingId ?? item?.id ?? index + 1
      const unitPrice = asNumber(item?.unitPrice)
    return {
        ...item,
        listingId,
        sellerLabel: String(item?.sellerName || item?.sellerLabel || item?.seller?.username || item?.author?.username || item?.displayName || '卖家信息待确认'),
        goodsTypeLabel: goodsTypeLabel(item?.goodsType),
        deliveryLabel: deliveryLabel(item?.deliveryMode),
        fulfillmentLabel: fulfillmentLabel(item),
        trustLabel: trustLabel(item?.status),
        shipmentLabel: shipmentLabel(item?.status),
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
        goodsTypeLabel: goodsTypeLabel(item?.goodsType),
        statusLabel: orderStatusLabel(item?.status),
        fundsLabel: fundsLabel(item?.escrowStatus || item?.fundState || item?.fundsStatus || item?.status),
        fulfillmentLabel: fulfillmentStateLabel(item),
        nextActionLabel: nextOrderActionLabel(item),
        lifecycleSteps: buildLifecycleSteps(item),
        totalAmountText: amountText(totalAmount),
        autoConfirmText: autoConfirmText(item)
      }
    }),
    disputes: safeDisputes.map((item, index) => ({
      ...item,
      disputeId: item?.disputeId ?? index + 1,
      goodsTypeLabel: goodsTypeLabel(item?.goodsType),
      statusLabel: disputeStatusLabel(item?.status),
      fundStateLabel: fundStateLabel(item?.fundState || item?.escrowStatus || item?.fundsStatus),
      nextActionLabel: nextDisputeActionLabel(item)
    })),
    addresses: safeAddresses.map((item, index) => ({
      ...item,
      addressId: item?.addressId ?? index + 1,
      addressLine: addressLine(item),
      defaultLabel: item?.defaultAddress ? '默认地址' : ''
    })),
    inventory: safeInventory.map((item, index) => ({
      ...item,
      inventoryUnitId: item?.inventoryUnitId ?? item?.id ?? index + 1,
      statusLabel: inventoryStatusLabel(item?.status)
    }))
  }
}
