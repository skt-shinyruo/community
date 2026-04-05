import http from '../http'
import { unwrapResultBody } from '../result'

export async function listMarketListings(params = {}) {
  const resp = await http.get('/api/market/listings', { params })
  const { data, traceId } = unwrapResultBody(resp.data, '查询市场商品列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getMarketListingDetail(listingId) {
  const resp = await http.get(`/api/market/listings/${encodeURIComponent(listingId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '查询市场商品详情')
  return { data: data || {}, traceId }
}

export async function createMarketListing(payload) {
  const resp = await http.post('/api/market/listings', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建市场商品')
  return { data: data || {}, traceId }
}

export async function createMarketOrder(payload) {
  const resp = await http.post('/api/market/orders', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建市场订单')
  return { data: data || {}, traceId }
}

export async function listMyMarketListings() {
  const resp = await http.get('/api/market/my-listings')
  const { data, traceId } = unwrapResultBody(resp.data, '查询我的出售商品')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listMarketInventory(listingId) {
  const resp = await http.get(`/api/market/listings/${encodeURIComponent(listingId)}/inventory`)
  const { data, traceId } = unwrapResultBody(resp.data, '查询库存列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function addMarketInventory(listingId, payload) {
  const resp = await http.post(`/api/market/listings/${encodeURIComponent(listingId)}/inventory`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '追加库存')
  return { data: data || {}, traceId }
}

export async function invalidateMarketInventory(inventoryUnitId) {
  const resp = await http.post(`/api/market/inventory/${encodeURIComponent(inventoryUnitId)}/invalidate`)
  const { data, traceId } = unwrapResultBody(resp.data, '失效库存')
  return { data: data || {}, traceId }
}

export async function listBuyingMarketOrders() {
  const resp = await http.get('/api/market/orders/buying')
  const { data, traceId } = unwrapResultBody(resp.data, '查询我的购买订单')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function listSellingMarketOrders() {
  const resp = await http.get('/api/market/orders/selling')
  const { data, traceId } = unwrapResultBody(resp.data, '查询我的出售订单')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function getMarketOrderDetail(orderId) {
  const resp = await http.get(`/api/market/orders/${encodeURIComponent(orderId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '查询市场订单详情')
  return { data: data || {}, traceId }
}

export async function openMarketDispute(orderId, payload) {
  const resp = await http.post(`/api/market/orders/${encodeURIComponent(orderId)}/disputes`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '发起申诉')
  return { data: data || {}, traceId }
}

export async function sellerAcceptMarketDispute(disputeId, payload) {
  const resp = await http.post(`/api/market/disputes/${encodeURIComponent(disputeId)}/seller-accept`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '卖家同意退款')
  return { data: data || {}, traceId }
}

export async function sellerRejectMarketDispute(disputeId, payload) {
  const resp = await http.post(`/api/market/disputes/${encodeURIComponent(disputeId)}/seller-reject`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '卖家拒绝退款')
  return { data: data || {}, traceId }
}

export async function listAdminMarketDisputes() {
  const resp = await http.get('/api/admin/market/disputes')
  const { data, traceId } = unwrapResultBody(resp.data, '查询争议列表')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function adminResolveMarketDispute(disputeId, action, payload) {
  const safeAction = action === 'release' ? 'resolve-release' : 'resolve-refund'
  const resp = await http.post(`/api/admin/market/disputes/${encodeURIComponent(disputeId)}/${safeAction}`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '处理争议')
  return { data: data || {}, traceId }
}

export async function listMarketAddresses() {
  const resp = await http.get('/api/market/addresses')
  const { data, traceId } = unwrapResultBody(resp.data, '查询地址簿')
  return { data: Array.isArray(data) ? data : [], traceId }
}

export async function createMarketAddress(payload) {
  const resp = await http.post('/api/market/addresses', payload)
  const { data, traceId } = unwrapResultBody(resp.data, '创建收货地址')
  return { data: data || {}, traceId }
}

export async function updateMarketAddress(addressId, payload) {
  const resp = await http.put(`/api/market/addresses/${encodeURIComponent(addressId)}`, payload)
  const { data, traceId } = unwrapResultBody(resp.data, '更新收货地址')
  return { data: data || {}, traceId }
}

export async function deleteMarketAddress(addressId) {
  const resp = await http.delete(`/api/market/addresses/${encodeURIComponent(addressId)}`)
  const { data, traceId } = unwrapResultBody(resp.data, '删除收货地址')
  return { data: data || {}, traceId }
}
