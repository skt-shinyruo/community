import http from '../http'
import { unwrapResultBody } from '../result'

export async function getWalletSummary() {
  const resp = await http.get('/api/wallet/summary')
  const { data, traceId } = unwrapResultBody(resp.data, '查询钱包概览')
  return { data: data || {}, traceId }
}

export async function createRecharge(payload) {
  const resp = await http.post('/api/wallet/recharges', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发起充值')
  return { data: data || {}, traceId }
}

export async function createWithdrawal(payload) {
  const resp = await http.post('/api/wallet/withdrawals', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发起提现')
  return { data: data || {}, traceId }
}

export async function createTransfer(payload) {
  const resp = await http.post('/api/wallet/transfers', payload)
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
