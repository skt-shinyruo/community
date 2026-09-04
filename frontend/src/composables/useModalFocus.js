// 弹窗焦点管理：打开后聚焦 [data-autofocus] 或首个可操作控件（都没有时聚焦弹窗自身），
// Tab / Shift+Tab 圈定在弹窗内，卸载后焦点恢复到触发控件。约定见 docs/handbook/frontend.md。
import { nextTick, onBeforeUnmount, onMounted } from 'vue'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(', ')

export function useModalFocus(dialogRef) {
  let previousActiveElement = null

  function focusableElements() {
    const dialog = dialogRef.value
    if (!dialog) return []
    return [...dialog.querySelectorAll(FOCUSABLE_SELECTOR)]
  }

  function onKeydown(event) {
    if (event?.key !== 'Tab') return
    const dialog = dialogRef.value
    if (!dialog) return
    const items = focusableElements()
    if (items.length === 0) {
      event.preventDefault()
      if (!dialog.hasAttribute('tabindex')) dialog.setAttribute('tabindex', '-1')
      dialog.focus()
      return
    }
    const first = items[0]
    const last = items[items.length - 1]
    const active = document.activeElement
    const leavesDialog = !dialog.contains(active)
    const wraps = event.shiftKey ? active === first || leavesDialog : active === last || leavesDialog
    if (wraps) {
      event.preventDefault()
      const target = event.shiftKey ? last : first
      target.focus()
    }
  }

  onMounted(async () => {
    previousActiveElement = document.activeElement
    await nextTick()
    const dialog = dialogRef.value
    if (!dialog) return
    const target = dialog.querySelector('[data-autofocus]') || focusableElements()[0] || dialog
    if (target === dialog && !dialog.hasAttribute('tabindex')) dialog.setAttribute('tabindex', '-1')
    target.focus()
  })

  onBeforeUnmount(() => {
    const previous = previousActiveElement
    previousActiveElement = null
    if (previous && typeof previous.focus === 'function' && document.contains(previous)) {
      previous.focus()
    }
  })

  return { onKeydown }
}
