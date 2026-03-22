import http from '../http'
import { unwrapResultBody } from '../result'

export async function listAdminRewardItems() {
  const resp = await http.get('/api/growth/admin/rewards/items')
  const { data, traceId } = unwrapResultBody(resp.data, '查询奖励商品')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function upsertAdminRewardItem(payload) {
  const resp = await http.post('/api/growth/admin/rewards/items', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '保存奖励商品')
  return { data: data || null, traceId }
}

export async function listAdminRewardOrders() {
  const resp = await http.get('/api/growth/admin/rewards/orders')
  const { data, traceId } = unwrapResultBody(resp.data, '查询奖励订单')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function processAdminRewardOrder(payload) {
  const resp = await http.post('/api/growth/admin/rewards/orders/action', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '处理奖励订单')
  return { data: data || null, traceId }
}

export async function getAdminRewardMetrics() {
  const resp = await http.get('/api/growth/admin/rewards/metrics')
  const { data, traceId } = unwrapResultBody(resp.data, '查询奖励运营指标')
  return { data: data || {}, traceId }
}
