import http from '../http'
import { unwrapResultBody } from '../result'

export async function listVirtualListings(params = {}) {
  const resp = await http.get('/api/market/virtual/listings', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询虚拟商品列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getVirtualListingDetail(listingId) {
  const resp = await http.get(`/api/market/virtual/listings/${encodeURIComponent(listingId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '查询虚拟商品详情')
  return { data: data || {}, traceId }
}

export async function createVirtualListing(payload) {
  const resp = await http.post('/api/market/virtual/listings', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建虚拟商品')
  return { data: data || {}, traceId }
}

export async function createVirtualOrder(payload) {
  const resp = await http.post('/api/market/virtual/orders', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建虚拟商品订单')
  return { data: data || {}, traceId }
}

export async function openVirtualDispute(orderId, payload) {
  const resp = await http.post(`/api/market/virtual/orders/${encodeURIComponent(orderId)}/disputes`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发起申诉')
  return { data: data || {}, traceId }
}

export async function sellerAcceptVirtualDispute(disputeId, payload) {
  const resp = await http.post(`/api/market/virtual/disputes/${encodeURIComponent(disputeId)}/seller-accept`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '卖家同意退款')
  return { data: data || {}, traceId }
}

export async function sellerRejectVirtualDispute(disputeId, payload) {
  const resp = await http.post(`/api/market/virtual/disputes/${encodeURIComponent(disputeId)}/seller-reject`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '卖家拒绝退款')
  return { data: data || {}, traceId }
}

export async function listAdminVirtualDisputes() {
  const resp = await http.get('/api/admin/market/virtual/disputes')
  const { data, traceId } = unwrapResultBody(resp.data, '查询争议列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function adminResolveVirtualDispute(disputeId, action, payload) {
  const safeAction = action === 'release' ? 'resolve-release' : 'resolve-refund'
  const resp = await http.post(`/api/admin/market/virtual/disputes/${encodeURIComponent(disputeId)}/${safeAction}`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '处理争议')
  return { data: data || {}, traceId }
}
