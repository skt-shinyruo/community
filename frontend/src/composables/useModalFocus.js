import { nextTick, onBeforeUnmount, onMounted, unref, watch } from 'vue'

const FOCUSABLE_SELECTOR = [
  '[data-modal-initial-focus]',
  'button:not([disabled])',
  'a[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',')

function focusableElements(dialog) {
  return Array.from(dialog?.querySelectorAll?.(FOCUSABLE_SELECTOR) || [])
    .filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true')
}

export function useModalFocus(dialogRef, { active = true } = {}) {
  let mounted = false
  let listening = false
  let restoreTarget = null
  let activation = 0

  const isActive = () => Boolean(unref(active))

  const onDocumentKeydown = (event) => {
    if (event.key !== 'Tab' || !isActive()) return
    const dialog = unref(dialogRef)
    if (!dialog || !globalThis.document?.contains(dialog)) return
    const elements = focusableElements(dialog)
    if (elements.length === 0) {
      event.preventDefault()
      dialog.focus()
      return
    }

    const first = elements[0]
    const last = elements[elements.length - 1]
    const current = globalThis.document?.activeElement
    if (event.shiftKey && (current === first || !dialog.contains(current))) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && (current === last || !dialog.contains(current))) {
      event.preventDefault()
      first.focus()
    }
  }

  const deactivate = ({ restore = true } = {}) => {
    activation += 1
    if (listening) globalThis.document?.removeEventListener?.('keydown', onDocumentKeydown, true)
    listening = false
    const target = restoreTarget
    restoreTarget = null
    if (restore && target?.focus && globalThis.document?.contains(target)) target.focus()
  }

  const activate = () => {
    if (!mounted || !isActive()) return
    deactivate({ restore: false })
    restoreTarget = globalThis.document?.activeElement || null
    globalThis.document?.addEventListener?.('keydown', onDocumentKeydown, true)
    listening = true
    const currentActivation = ++activation
    void nextTick(() => {
      if (currentActivation !== activation || !isActive()) return
      const dialog = unref(dialogRef)
      const target = focusableElements(dialog)[0] || dialog
      target?.focus?.()
    })
  }

  const stop = watch(
    () => isActive(),
    (open, wasOpen) => {
      if (!mounted || open === wasOpen) return
      if (open) activate()
      else deactivate()
    },
    { flush: 'post' }
  )

  onMounted(() => {
    mounted = true
    if (isActive()) activate()
  })
  onBeforeUnmount(() => {
    mounted = false
    stop()
    deactivate()
  })

  return { activate, deactivate }
}
