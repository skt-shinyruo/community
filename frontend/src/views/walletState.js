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
  if (status === 'ACTIVE') return '钱包状态正常，可继续消费、转账与提现。'
  if (status === 'UNKNOWN') return '钱包状态暂不可用，余额以当前可见数据为准。'
  return '钱包状态正常，可继续消费、转账与提现。'
}

function txnLabel(txnType, amount) {
  const type = String(txnType || '').trim().toUpperCase()

  if (type === 'TRANSFER') {
    return amount < 0 ? '转账转出' : '转账转入'
  }
  if (type === 'RECHARGE') return '充值到账'
  if (type === 'WITHDRAW') return '提现申请'
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
