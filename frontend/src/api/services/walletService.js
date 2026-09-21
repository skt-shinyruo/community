import http from '../http'
import { unwrapResultBody } from '../result'
import { writeAttemptConfig } from '../writeAttempt'

/**
 * 幂等写入尝试句柄：持有一个高危写请求的 Idempotency-Key。
 * @typedef {ReturnType<typeof import('../writeAttempt').createWriteAttempt>} WriteAttempt
 */

export async function getWalletSummary() {
  const resp = await http.get('/api/wallet/summary')
  const { data, traceId } = unwrapResultBody(resp.data, '查询钱包概览')
  return { data: data || {}, traceId }
}

export async function getWalletCapabilities() {
  const resp = await http.get('/api/wallet/capabilities')
  const { data, traceId } = unwrapResultBody(resp.data, '查询钱包能力')
  return { data: data || {}, traceId }
}

export async function getWalletTransactions(limit = 12) {
  const resp = await http.get('/api/wallet/transactions', { params: { limit } })
  const { data, traceId } = unwrapResultBody(resp.data, '查询钱包流水')
  return { data: Array.isArray(data) ? data : [], traceId }
}

/**
 * @param {Record<string, unknown>} payload
 * @param {{ writeAttempt?: WriteAttempt }} [options]
 */
export async function createRecharge(payload, { writeAttempt } = {}) {
  const resp = await http.post('/api/wallet/recharges', payload, writeAttemptConfig(writeAttempt))
  const { data, traceId } = unwrapResultBody(resp.data, '领取测试积分')
  return { data: data || {}, traceId }
}

/**
 * @param {Record<string, unknown>} payload
 * @param {{ writeAttempt?: WriteAttempt }} [options]
 */
export async function createWithdrawal(payload, { writeAttempt } = {}) {
  const resp = await http.post('/api/wallet/withdrawals', payload, writeAttemptConfig(writeAttempt))
  const { data, traceId } = unwrapResultBody(resp.data, '销毁测试积分')
  return { data: data || {}, traceId }
}

/**
 * @param {Record<string, unknown>} payload
 * @param {{ writeAttempt?: WriteAttempt }} [options]
 */
export async function createTransfer(payload, { writeAttempt } = {}) {
  const resp = await http.post('/api/wallet/transfers', payload, writeAttemptConfig(writeAttempt))
  const { data, traceId } = unwrapResultBody(resp.data, '发起转账')
  return { data: data || {}, traceId }
}

export async function freezeWallet(payload) {
  const resp = await http.post('/api/wallet/admin/freeze', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '冻结钱包')
  return { data: data || null, traceId }
}

export async function reverseWalletTxn(payload) {
  const resp = await http.post('/api/wallet/admin/reverse', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '回滚交易')
  return { data: data || null, traceId }
}
