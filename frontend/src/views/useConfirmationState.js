// @ts-check
import { reactive } from 'vue'

// 资损 / 危险操作的二次确认状态（UiModalConfirm）：网盘删除、钱包转账 / 销毁共用。
// 确认后立即关闭弹窗，动作本身的 busy 语义由调用方的提交流程承担；busy 期间不允许发起新确认。
export function useConfirmationState({ isBusy }) {
  const confirmation = reactive({
    open: false,
    title: '',
    message: '',
    confirmText: '确认',
    variant: 'primary',
    action: /** @type {null | (() => Promise<void> | void)} */ (null)
  })

  function confirm({ title, message, confirmText = '确认', variant = 'primary' }, action) {
    if (isBusy.value || typeof action !== 'function') return
    confirmation.title = title
    confirmation.message = message
    confirmation.confirmText = confirmText
    confirmation.variant = variant
    confirmation.action = action
    confirmation.open = true
  }

  function closeConfirmation() {
    confirmation.open = false
    confirmation.action = null
  }

  async function runConfirmation() {
    const action = confirmation.action
    if (!action) return
    closeConfirmation()
    await action()
  }

  return { confirmation, confirm, closeConfirmation, runConfirmation }
}
