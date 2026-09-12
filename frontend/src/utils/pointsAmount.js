// 积分金额的统一入口校验：充值 / 转账 / 销毁 / 商品定价只接受正整数积分。
// 后端按长整型入账，小数会被截断；必须在提交前拒绝，保证确认弹窗复述的金额与实际入账一致。
export function parsePointsAmount(raw) {
  const value = typeof raw === 'number' ? raw : Number(String(raw ?? '').trim())
  if (!Number.isFinite(value) || value <= 0) {
    return { valid: false, amount: 0, message: '请输入正整数积分金额' }
  }
  if (!Number.isInteger(value)) {
    return { valid: false, amount: 0, message: '积分金额必须是整数，不支持小数' }
  }
  if (!Number.isSafeInteger(value)) {
    return { valid: false, amount: 0, message: '积分金额超出支持的范围' }
  }
  return { valid: true, amount: value, message: '' }
}
