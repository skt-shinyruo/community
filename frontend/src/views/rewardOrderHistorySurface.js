export function buildRewardOrderHistorySurface({ loading = false, error = '', orders = [] } = {}) {
  const orderList = Array.isArray(orders) ? orders : []

  let bodyState = 'list'
  if (error) {
    bodyState = 'error'
  } else if (loading && orderList.length === 0) {
    bodyState = 'loading'
  } else if (orderList.length === 0) {
    bodyState = 'empty'
  }

  return {
    title: '兑换记录',
    subtitle: '所有兑换结果都在这里保留状态快照，便于用户理解自己的兑换去向。',
    showHeader: true,
    canReturnToShop: true,
    bodyState
  }
}
