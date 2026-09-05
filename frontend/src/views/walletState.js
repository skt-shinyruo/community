function asNumber(value, fallback = 0) {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

function normalizeStatus(status, fallback = 'UNKNOWN') {
  const normalized = String(status || '').trim().toUpperCase()
  return normalized || fallback
}

function statusText(status) {
  if (status === 'FROZEN') return '钱包已冻结，当前仅保留查询能力。'
  if (status === 'CLOSED') return '钱包已关闭，如需恢复请联系管理员。'
  if (status === 'ACTIVE') return '钱包状态正常，可继续进行站内消费与转账。'
  if (status === 'UNKNOWN') return '钱包状态暂不可用，余额以当前可见数据为准。'
  return '钱包状态正常，可继续进行站内消费与转账。'
}

function txnLabel(txnType, amount) {
  const type = String(txnType || '').trim().toUpperCase()

  if (type === 'TRANSFER') {
    return amount < 0 ? '转账转出' : '转账转入'
  }
  if (type === 'TEST_CREDIT_GRANT') return '测试积分发放'
  if (type === 'TEST_CREDIT_DISCARD') return '测试积分销毁'
  if (type === 'RECHARGE') return '充值入账'
  if (type === 'WITHDRAW') return '提现'
  if (type === 'REWARD_ISSUE') return '活动补贴'
  if (type === 'OPENING_BALANCE') return '初始入账'
  if (type === 'REVERSAL') return '交易回滚'
  return '钱包交易'
}

function amountText(amount) {
  const normalized = asNumber(amount)
  if (normalized > 0) return `+${normalized}`
  if (normalized < 0) return String(normalized)
  return '0'
}

function txnMetaText(txn) {
  const counterpart = String(txn?.counterpartLabel || '').trim()
  const remark = String(txn?.remark || '').trim()
  return counterpart || remark || '系统记账'
}

// 流水追加分页只依赖后端 limit 语义（GET /api/wallet/transactions 归一化到 1..50，默认 12）：
// 请求窗口按 pageSize 递增，返回条数少于当前窗口即证明到底；到达上限后停止追加。
export const WALLET_FEED_PAGE_SIZE = 12
export const WALLET_FEED_MAX_LIMIT = 50

function normalizeLimit(value, fallback) {
  const next = Number(value)
  return Number.isFinite(next) && next > 0 ? Math.trunc(next) : fallback
}

export function nextWalletFeedLimit(currentLimit, pageSize = WALLET_FEED_PAGE_SIZE, maxLimit = WALLET_FEED_MAX_LIMIT) {
  const current = normalizeLimit(currentLimit, 0)
  const step = normalizeLimit(pageSize, WALLET_FEED_PAGE_SIZE)
  const cap = normalizeLimit(maxLimit, WALLET_FEED_MAX_LIMIT)
  return Math.min(cap, current + step)
}

export function walletFeedHasMore({ count, limit, maxLimit = WALLET_FEED_MAX_LIMIT } = {}) {
  const size = normalizeLimit(count, 0)
  const current = normalizeLimit(limit, WALLET_FEED_PAGE_SIZE)
  const cap = normalizeLimit(maxLimit, WALLET_FEED_MAX_LIMIT)
  return size >= current && current < cap
}

export function walletFeedExhausted({ count, limit } = {}) {
  const size = normalizeLimit(count, 0)
  const current = normalizeLimit(limit, WALLET_FEED_PAGE_SIZE)
  return size < current
}

// 资损动作（转账转出、销毁测试积分）的二次确认文案：金额与对方在确认弹窗中复述，
// 确认后才进入对应 WriteAttempt 的提交流程。
export function walletTransferConfirmation({ toUserId, amount } = {}) {
  const target = String(toUserId || '').trim()
  const value = asNumber(amount)
  return {
    title: '确认转账',
    message: `将向用户 ${target} 转出 ${value} 积分；转账不可撤销，请确认对方信息无误。`,
    confirmText: '确认转账'
  }
}

export function walletDiscardConfirmation({ amount } = {}) {
  const value = asNumber(amount)
  return {
    title: '确认销毁测试积分',
    message: `将销毁 ${value} 测试积分并永久减少钱包余额；该操作不可撤销。`,
    confirmText: '确认销毁'
  }
}

export function buildWalletState({ summary, txns } = {}) {
  const safeSummary = summary && typeof summary === 'object' ? summary : {}
  const safeTxns = Array.isArray(txns) ? txns : []
  const status = normalizeStatus(safeSummary.status, 'UNKNOWN')

  return {
    hero: {
      balance: Math.max(0, asNumber(safeSummary.balance)),
      status,
      statusText: statusText(status)
    },
    feed: safeTxns.map((txn, index) => {
      const amount = asNumber(txn?.amount)
      return {
        key: String(txn?.txnRef || txn?.txnId || `${txn?.txnType || 'txn'}-${index}`),
        label: txnLabel(txn?.txnType, amount),
        amount,
        amountText: `${amountText(amount)} 积分`,
        meta: txnMetaText(txn),
        status: normalizeStatus(txn?.status || 'SUCCEEDED', 'SUCCEEDED')
      }
    })
  }
}
