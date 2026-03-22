import http from '../http'
import { unwrapResultBody } from '../result'

export async function listRewardItems() {
  const resp = await http.get('/api/growth/shop/items')
  const { data, traceId } = unwrapResultBody(resp.data, '查询奖励商城')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getRewardItem(itemId) {
  const resp = await http.get(`/api/growth/shop/items/${encodeURIComponent(itemId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '查询奖励详情')
  return { data: data || {}, traceId }
}

export async function redeemReward({ itemId, requestId }) {
  const resp = await http.post('/api/growth/shop/redeem', {
    itemId,
    requestId
  })
  const { data, traceId } = unwrapResultBody(resp.data, '兑换奖励')
  return { data: data || {}, traceId }
}

export async function listRewardOrders() {
  const resp = await http.get('/api/growth/shop/orders')
  const { data, traceId } = unwrapResultBody(resp.data, '查询兑换记录')
  return { data: Array.isArray(data) ? data : [], traceId }
}
