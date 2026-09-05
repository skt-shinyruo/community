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

function listingStatusLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ACTIVE') return '在售'
  if (normalized === 'SOLD_OUT') return '已售罄'
  if (normalized === 'PAUSED') return '已暂停'
  if (normalized === 'CLOSED') return '已关闭'
  return '待处理'
}

// UiBadge variant：在售 success、售罄 warning，其余回落 default，库存/价格状态在视图层可区分。
function listingStatusVariant(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ACTIVE') return 'success'
  if (normalized === 'SOLD_OUT') return 'warning'
  return 'default'
}

// UiBadge variant：已完成 success、争议与托管失败 danger、资金/退款处理中 pending（pending
// 语义见 frontend.md 一致性表）、托管/交付/发货等履约进行中 accent，取消/退款等中性终态与
// 未知状态保守回落 default；文案仍由 statusLabel 承担，不靠颜色单独表达。
function orderStatusVariant(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'COMPLETED') return 'success'
  if (normalized === 'DISPUTED' || normalized === 'DISPUTE_REFUND_PENDING' || normalized === 'DISPUTE_RELEASE_PENDING') return 'danger'
  if (normalized === 'ESCROW_FAILED') return 'danger'
  if (normalized === 'ESCROW_PENDING' || normalized === 'RELEASE_PENDING'
    || normalized === 'REFUND_PENDING' || normalized === 'ESCROW_CANCEL_PENDING') return 'pending'
  if (normalized === 'ESCROWED' || normalized === 'DELIVERED' || normalized === 'SHIPPED') return 'accent'
  return 'default'
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

const UNKNOWN_ORDER_STATUS_FACT = {
  statusLabel: '状态待确认',
  fundsLabel: '资金状态待确认',
  fulfillment: 'pending',
  nextActionLabel: '查看订单详情',
  allowedActions: [],
  fundsStep: 'pending',
  confirmationStep: 'pending',
  disputed: false
}

const ORDER_STATUS_FACTS = {
  ESCROW_PENDING: {
    statusLabel: '托管处理中', fundsLabel: '托管处理中', fulfillment: 'pending',
    nextActionLabel: '等待资金托管', allowedActions: ['cancel'], fundsStep: 'processing', confirmationStep: 'pending', disputed: false
  },
  ESCROWED: {
    statusLabel: '已托管', fundsLabel: '托管中', fulfillment: 'pending',
    nextActionLabel: '等待卖家履约', allowedActions: ['fulfill', 'cancel'], fundsStep: 'complete', confirmationStep: 'pending', disputed: false
  },
  DELIVERED: {
    statusLabel: '待确认', fundsLabel: '托管中', fulfillment: 'delivered',
    nextActionLabel: '等待买家确认完成', allowedActions: ['confirm', 'dispute'], confirmButtonText: '确认完成',
    fundsStep: 'complete', confirmationStep: 'active', disputed: false
  },
  SHIPPED: {
    statusLabel: '已发货', fundsLabel: '托管中', fulfillment: 'shipped',
    nextActionLabel: '等待买家确认收货', allowedActions: ['confirm', 'dispute'], confirmButtonText: '确认收货',
    fundsStep: 'complete', confirmationStep: 'active', disputed: false
  },
  RELEASE_PENDING: {
    statusLabel: '放款处理中', fundsLabel: '放款处理中', fulfillment: 'fulfilled',
    nextActionLabel: '等待放款完成', allowedActions: [], fundsStep: 'complete', confirmationStep: 'active', disputed: false
  },
  COMPLETED: {
    statusLabel: '已完成', fundsLabel: '已放款', fulfillment: 'completed',
    nextActionLabel: '订单已完成', allowedActions: [], fundsStep: 'complete', confirmationStep: 'complete', disputed: false
  },
  REFUND_PENDING: {
    statusLabel: '退款处理中', fundsLabel: '退款处理中', fulfillment: 'pending',
    nextActionLabel: '等待退款完成', allowedActions: [], fundsStep: 'complete', confirmationStep: 'active', disputed: false
  },
  CANCELLED: {
    statusLabel: '已取消', fundsLabel: '已取消', fulfillment: 'pending',
    nextActionLabel: '订单已取消', allowedActions: [], fundsStep: 'pending', confirmationStep: 'complete', disputed: false
  },
  ESCROW_CANCEL_PENDING: {
    statusLabel: '取消处理中', fundsLabel: '取消托管处理中', fulfillment: 'pending',
    nextActionLabel: '等待取消订单', allowedActions: [], fundsStep: 'processing', confirmationStep: 'active', disputed: false
  },
  ESCROW_FAILED: {
    statusLabel: '托管失败', fundsLabel: '托管失败', fulfillment: 'pending',
    nextActionLabel: '资金托管失败', allowedActions: [], fundsStep: 'processing', confirmationStep: 'complete', disputed: false
  },
  DISPUTED: {
    statusLabel: '申诉中', fundsLabel: '托管中', fulfillment: 'disputed',
    nextActionLabel: '等待争议处理', allowedActions: [], fundsStep: 'complete', confirmationStep: 'active', disputed: true
  },
  DISPUTE_REFUND_PENDING: {
    statusLabel: '争议退款处理中', fundsLabel: '退款处理中', fulfillment: 'disputed',
    nextActionLabel: '等待争议退款完成', allowedActions: [], fundsStep: 'complete', confirmationStep: 'active', disputed: true
  },
  DISPUTE_RELEASE_PENDING: {
    statusLabel: '争议放款处理中', fundsLabel: '放款处理中', fulfillment: 'disputed',
    nextActionLabel: '等待争议放款完成', allowedActions: [], fundsStep: 'complete', confirmationStep: 'active', disputed: true
  },
  REFUNDED: {
    statusLabel: '已退款', fundsLabel: '已退款', fulfillment: 'pending',
    nextActionLabel: '退款已完成', allowedActions: [], fundsStep: 'complete', confirmationStep: 'complete', disputed: false
  }
}

function orderStatusFact(status) {
  return ORDER_STATUS_FACTS[normalizeStatus(status)] || UNKNOWN_ORDER_STATUS_FACT
}

function completedFulfillmentLabel(item) {
  return String(item?.goodsType || '').trim().toUpperCase() === 'PHYSICAL' ? '已发货' : '已交付'
}

function fulfillmentStateLabel(item, fact = orderStatusFact(item?.status)) {
  if (fact.fulfillment === 'delivered') return '已交付'
  if (fact.fulfillment === 'shipped') return '已发货'
  if (fact.fulfillment === 'fulfilled') return completedFulfillmentLabel(item)
  if (fact.fulfillment === 'completed') return '已完成'
  if (fact.fulfillment === 'disputed') return '争议处理中'
  if (String(item?.goodsType || '').trim().toUpperCase() === 'VIRTUAL') return deliveryLabel(item?.deliveryModeSnapshot)
  return '等待履约'
}

function buildLifecycleSteps(item, fact = orderStatusFact(item?.status)) {
  const fulfillmentComplete = fact.fulfillment !== 'pending'
  const fundsStepLabel = fact.fundsStep === 'complete'
    ? '资金托管'
    : fact.fundsStep === 'processing' ? fact.fundsLabel : '等待托管'

  return [
    { key: 'created', label: '已创建', state: 'complete' },
    { key: 'funds', label: fundsStepLabel, state: fact.fundsStep === 'processing' ? 'active' : fact.fundsStep },
    { key: 'fulfillment', label: fulfillmentComplete ? fulfillmentStateLabel(item, fact) : '等待履约', state: fulfillmentComplete ? 'complete' : 'pending' },
    { key: 'confirmation', label: fact.confirmationStep === 'complete' ? fact.statusLabel : '待确认', state: fact.confirmationStep },
    { key: 'dispute', label: fact.disputed ? '争议处理中' : '无争议', state: fact.disputed ? 'active' : 'pending' }
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

// 库存状态排序秩：可售在前、失效在后，未知状态垫底；UiTable 状态列排序使用。
function inventoryStatusRank(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'AVAILABLE') return 0
  if (normalized === 'LOCKED' || normalized === 'RESERVED') return 1
  if (normalized === 'SOLD') return 2
  if (normalized === 'INVALIDATED') return 3
  return 4
}

// UiBadge variant：可售 success、锁定 pending、售出 warning，失效与未知回落 default。
function inventoryStatusVariant(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'AVAILABLE') return 'success'
  if (normalized === 'LOCKED' || normalized === 'RESERVED') return 'pending'
  if (normalized === 'SOLD') return 'warning'
  return 'default'
}

// 与追加库存表单的可选项文案保持一致（兑换码 / 文本 / 链接），未知类型透传原始值。
function inventoryPayloadTypeLabel(payloadType) {
  const normalized = String(payloadType || '').trim().toUpperCase()
  if (normalized === 'CODE') return '兑换码'
  if (normalized === 'TEXT') return '文本'
  if (normalized === 'LINK') return '链接'
  return normalized || '未知类型'
}

// UiTable 排序钩子：视图把表头 sort 事件转成下一份排序状态（换列回到 asc、同列切换方向），
// 排序在已加载投影上本地完成——后端列表接口没有排序参数，与页内搜索是同一取舍。
export function nextTableSort(current, key) {
  const safeKey = String(key || '')
  if (!safeKey) return { key: '', direction: 'asc' }
  const currentKey = String(current?.key || '')
  const currentDirection = current?.direction === 'desc' ? 'desc' : 'asc'
  if (currentKey !== safeKey) return { key: safeKey, direction: 'asc' }
  return { key: safeKey, direction: currentDirection === 'asc' ? 'desc' : 'asc' }
}

export function sortMarketInventory(items, sort) {
  const safeItems = Array.isArray(items) ? [...items] : []
  const direction = sort?.direction === 'desc' ? -1 : 1
  const comparators = {
    status: (a, b) =>
      (asNumber(a?.statusRank) - asNumber(b?.statusRank)) ||
      String(a?.statusLabel || '').localeCompare(String(b?.statusLabel || ''), 'zh'),
    payloadType: (a, b) =>
      String(a?.payloadTypeLabel || '').localeCompare(String(b?.payloadTypeLabel || ''), 'zh')
  }
  const comparator = comparators[String(sort?.key || '')]
  if (!comparator) return safeItems
  return safeItems.sort((a, b) => comparator(a, b) * direction)
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

function autoConfirmText(item, fact = orderStatusFact(item?.status)) {
  if (item?.autoConfirmAt) return `自动确认 ${item.autoConfirmAt}`
  if (String(item?.goodsType || '').trim().toUpperCase() === 'PHYSICAL' && fact.fulfillment === 'shipped') {
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

// 市场页内搜索：后端列表接口只有页码参数（规范不修改后端 API），关键词在已加载的商品投影上
// 过滤标题、描述和卖家标签；壳搜索只覆盖帖子/标签/用户，不承接市场。
export function filterMarketListings(listings, keyword) {
  const safeListings = Array.isArray(listings) ? listings : []
  const query = String(keyword || '').trim().toLowerCase()
  if (!query) return safeListings
  return safeListings.filter((item) => [
    item?.title,
    item?.description,
    item?.sellerLabel
  ].some((field) => String(field || '').toLowerCase().includes(query)))
}

export function mergeMarketPage(currentItems, nextItems, idField) {
  const merged = []
  const positions = new Map()
  for (const item of [
    ...(Array.isArray(currentItems) ? currentItems : []),
    ...(Array.isArray(nextItems) ? nextItems : [])
  ]) {
    const id = item?.[idField]
    if (id == null || id === '') {
      merged.push(item)
      continue
    }
    const key = String(id)
    const existingPosition = positions.get(key)
    if (existingPosition == null) {
      positions.set(key, merged.length)
      merged.push(item)
    } else {
      merged[existingPosition] = item
    }
  }
  return merged
}

export function buildMarketState({ listings, orders, disputes, addresses, inventory } = {}) {
  const safeListings = Array.isArray(listings) ? listings : []
  const safeOrders = Array.isArray(orders) ? orders : []
  const safeDisputes = Array.isArray(disputes) ? disputes : []
  const safeAddresses = Array.isArray(addresses) ? addresses : []
  const safeInventory = Array.isArray(inventory) ? inventory : []

  return {
    listings: safeListings.map((item) => {
      const unitPrice = asNumber(item?.unitPrice)
      return {
        ...item,
        sellerLabel: String(item?.sellerUserId || '卖家信息待确认'),
        goodsTypeLabel: goodsTypeLabel(item?.goodsType),
        deliveryLabel: deliveryLabel(item?.deliveryMode),
        fulfillmentLabel: fulfillmentLabel(item),
        trustLabel: trustLabel(item?.status),
        statusLabel: listingStatusLabel(item?.status),
        statusVariant: listingStatusVariant(item?.status),
        unitPriceText: amountText(unitPrice),
        stockText: stockText(item?.stockAvailable)
      }
    }),
    orders: safeOrders.map((item) => {
      const totalAmount = asNumber(item?.totalAmount)
      const fact = orderStatusFact(item?.status)
      return {
        ...item,
        goodsTypeLabel: goodsTypeLabel(item?.goodsType),
        statusLabel: fact.statusLabel,
        statusVariant: orderStatusVariant(item?.status),
        fundsLabel: fact.fundsLabel,
        fulfillmentLabel: fulfillmentStateLabel(item, fact),
        nextActionLabel: fact.nextActionLabel,
        allowedActions: fact.allowedActions,
        confirmButtonText: fact.confirmButtonText || '',
        lifecycleSteps: buildLifecycleSteps(item, fact),
        totalAmountText: amountText(totalAmount),
        autoConfirmText: autoConfirmText(item, fact)
      }
    }),
    disputes: safeDisputes.map((item) => ({
      ...item,
      goodsTypeLabel: goodsTypeLabel(item?.goodsType),
      statusLabel: disputeStatusLabel(item?.status),
      nextActionLabel: nextDisputeActionLabel(item)
    })),
    addresses: safeAddresses.map((item) => ({
      ...item,
      addressLine: addressLine(item),
      defaultLabel: item?.defaultAddress ? '默认地址' : ''
    })),
    inventory: safeInventory.map((item) => ({
      ...item,
      statusLabel: inventoryStatusLabel(item?.status),
      statusRank: inventoryStatusRank(item?.status),
      statusVariant: inventoryStatusVariant(item?.status),
      payloadTypeLabel: inventoryPayloadTypeLabel(item?.payloadType)
    }))
  }
}

// 确认完成 / 收货是放款给卖家的资损动作，取消订单会中止卖家履约并触发退款流程：
// 两者都先经 UiModalConfirm 复述金额与不可撤销后果（延续 WalletView 的确认写法），
// 再进入原提交流程；确认弹窗只做复述，不改变 allowedActions 权限语义。
export function marketOrderConfirmConfirmation({ totalAmountText, confirmButtonText } = {}) {
  const amount = String(totalAmountText || '').trim() || '托管资金'
  const action = String(confirmButtonText || '').trim() || '确认完成'
  return {
    title: action,
    message: `${action}后，托管的 ${amount} 将放款给卖家，操作不可撤销；如未收到商品或交付内容无效，请先发起申诉。`,
    confirmText: action,
    variant: 'danger'
  }
}

export function marketOrderCancelConfirmation({ totalAmountText } = {}) {
  const amount = String(totalAmountText || '').trim() || '托管资金'
  return {
    title: '取消订单',
    message: `取消后订单进入取消托管处理，托管的 ${amount} 将退回你的钱包，卖家不再继续履约；取消不可撤销。`,
    confirmText: '取消订单',
    variant: 'danger'
  }
}
