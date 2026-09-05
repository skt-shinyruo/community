// @ts-check
import { reactive } from 'vue'

// 网盘危险操作的二次确认状态：删除到回收站 / 彻底删除 / 撤销分享经 UiModalConfirm 确认。
// 确认后立即关闭弹窗，动作本身的 busy 语义由 runAction 承担；busy 期间不允许发起新确认。
export function useDriveConfirmation({ isBusy }) {
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
