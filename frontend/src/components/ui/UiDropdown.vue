<!-- 低频动作菜单：menu/menuitem 语义，trigger 携带 aria-haspopup/expanded/controls。
     click 与 Enter/Space/↓ 打开并聚焦首个可用项（↑ 聚焦末项）；菜单内 ↑/↓ 循环、Home/End 跳转、
     Enter/Space 激活；Escape、选中、trigger 再点击与外部 pointerdown 关闭，Escape 与选中关闭后
     焦点返回 trigger。浮层 teleport 到 body，按视口空间翻转并夹取在视口内。 -->
<template>
  <span ref="rootRef" class="ui-dropdown">
    <button
      ref="triggerRef"
      :id="triggerId"
      type="button"
      class="ui-dropdown__trigger"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="open ? menuId : undefined"
      :disabled="disabled"
      @click="onTriggerClick"
      @keydown="onTriggerKeydown"
    >
      <slot>{{ label }}</slot>
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        ref="menuRef"
        :id="menuId"
        role="menu"
        class="ui-dropdown__menu"
        :class="`ui-dropdown__menu--${resolvedPlacement}`"
        :aria-labelledby="triggerId"
        :style="menuStyle"
        @keydown="onMenuKeydown"
      >
        <button
          v-for="item in items"
          :key="itemKey(item)"
          type="button"
          role="menuitem"
          class="ui-dropdown__item"
          :class="{ 'ui-dropdown__item--danger': item.danger }"
          tabindex="-1"
          :aria-disabled="item.disabled ? 'true' : undefined"
          @click="selectItem(item)"
        >
          {{ item.label }}
        </button>
      </div>
    </Teleport>
  </span>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, useId } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] }, // [{ value, label, disabled?, danger? }]
  label: { type: String, default: '' }, // 默认 slot 缺省时的 trigger 文案；图标 trigger 由 slot 提供可访问名称
  disabled: { type: Boolean, default: false },
  placement: { type: String, default: 'bottom' } // bottom | top，视口空间不足时自动翻转
})

const emit = defineEmits(['select'])

const GAP = 4
const VIEWPORT_MARGIN = 8

const uid = useId()
const triggerId = `ui-dropdown-trigger-${uid}`
const menuId = `ui-dropdown-menu-${uid}`

const rootRef = ref(null)
const triggerRef = ref(null)
const menuRef = ref(null)
const open = ref(false)
const resolvedPlacement = ref('bottom')
const menuStyle = ref({ visibility: 'hidden' })

function itemKey(item) {
  return String(item.value)
}

function preferredPlacement() {
  return props.placement === 'top' ? 'top' : 'bottom'
}

function itemElements() {
  const menu = menuRef.value
  return menu ? [...menu.querySelectorAll('[role="menuitem"]')] : []
}

function enabledIndexes() {
  return props.items.map((item, index) => (item.disabled ? -1 : index)).filter((index) => index !== -1)
}

function focusEdge(which) {
  const enabled = enabledIndexes()
  if (!enabled.length) return
  const target = which === 'last' ? enabled[enabled.length - 1] : enabled[0]
  itemElements()[target]?.focus()
}

function moveFocus(step) {
  const elements = itemElements()
  const enabled = enabledIndexes()
  if (!enabled.length) return
  const position = enabled.indexOf(elements.indexOf(document.activeElement))
  const nextPosition = position === -1 ? (step > 0 ? 0 : enabled.length - 1) : (position + step + enabled.length) % enabled.length
  elements[enabled[nextPosition]]?.focus()
}

function updatePosition() {
  const trigger = triggerRef.value
  const menu = menuRef.value
  if (!trigger || !menu) return
  const triggerRect = trigger.getBoundingClientRect()
  const menuRect = menu.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight

  let placement = preferredPlacement()
  const spaceAbove = triggerRect.top
  const spaceBelow = viewportHeight - triggerRect.bottom
  if (placement === 'bottom' && spaceBelow < menuRect.height + GAP && spaceAbove > spaceBelow) {
    placement = 'top'
  } else if (placement === 'top' && spaceAbove < menuRect.height + GAP && spaceBelow > spaceAbove) {
    placement = 'bottom'
  }
  resolvedPlacement.value = placement

  const maxLeft = Math.max(VIEWPORT_MARGIN, viewportWidth - menuRect.width - VIEWPORT_MARGIN)
  const left = Math.min(Math.max(triggerRect.left, VIEWPORT_MARGIN), maxLeft)
  const belowTop = triggerRect.bottom + GAP
  const aboveTop = triggerRect.top - menuRect.height - GAP
  const maxTop = Math.max(VIEWPORT_MARGIN, viewportHeight - menuRect.height - VIEWPORT_MARGIN)
  const top = Math.min(Math.max(placement === 'bottom' ? belowTop : aboveTop, VIEWPORT_MARGIN), maxTop)

  menuStyle.value = { top: `${Math.round(top)}px`, left: `${Math.round(left)}px`, visibility: 'visible' }
}

function onDocumentPointerdown(event) {
  const target = event?.target
  if (rootRef.value?.contains(target) || menuRef.value?.contains(target)) return
  closeMenu()
}

async function openMenu(focus = 'first') {
  if (props.disabled || open.value) return
  resolvedPlacement.value = preferredPlacement()
  menuStyle.value = { visibility: 'hidden' }
  open.value = true
  await nextTick()
  updatePosition()
  window.addEventListener('resize', updatePosition)
  window.addEventListener('scroll', updatePosition, { capture: true, passive: true })
  document.addEventListener('pointerdown', onDocumentPointerdown, true)
  focusEdge(focus)
}

function closeMenu({ returnFocus = false } = {}) {
  if (!open.value) return
  open.value = false
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, { capture: true })
  document.removeEventListener('pointerdown', onDocumentPointerdown, true)
  if (returnFocus) triggerRef.value?.focus()
}

function selectItem(item) {
  if (!item || item.disabled) return
  emit('select', item)
  closeMenu({ returnFocus: true })
}

function onTriggerClick() {
  if (props.disabled) return
  if (open.value) closeMenu()
  else openMenu('first')
}

function onTriggerKeydown(event) {
  if (props.disabled) return
  const key = event?.key
  if (key === 'ArrowDown') {
    event.preventDefault()
    openMenu('first')
  } else if (key === 'ArrowUp') {
    event.preventDefault()
    openMenu('last')
  } else if (key === 'Enter' || key === ' ') {
    // preventDefault 抑制原生 click，避免与 click 处理器重复切换。
    event.preventDefault()
    openMenu('first')
  } else if (key === 'Escape' && open.value) {
    event.preventDefault()
    closeMenu()
  }
}

function onMenuKeydown(event) {
  const key = event?.key
  if (key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    closeMenu({ returnFocus: true })
  } else if (key === 'ArrowDown') {
    event.preventDefault()
    moveFocus(1)
  } else if (key === 'ArrowUp') {
    event.preventDefault()
    moveFocus(-1)
  } else if (key === 'Home') {
    event.preventDefault()
    focusEdge('first')
  } else if (key === 'End') {
    event.preventDefault()
    focusEdge('last')
  } else if (key === 'Enter' || key === ' ') {
    event.preventDefault()
    const elements = itemElements()
    selectItem(props.items[elements.indexOf(document.activeElement)])
  } else if (key === 'Tab') {
    closeMenu()
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, { capture: true })
  document.removeEventListener('pointerdown', onDocumentPointerdown, true)
})
</script>

<style scoped>
.ui-dropdown {
  display: inline-block;
}

.ui-dropdown__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: var(--control-height);
  padding: 0 var(--control-padding-x);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-2);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition:
    color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.ui-dropdown__trigger:hover:not(:disabled) {
  color: var(--text-1);
  border-color: var(--border-strong);
}

.ui-dropdown__trigger:focus-visible {
  box-shadow: var(--focus-ring);
}

.ui-dropdown__trigger:disabled {
  color: var(--muted);
  cursor: not-allowed;
}

.ui-dropdown__menu {
  position: fixed;
  top: 0;
  left: 0;
  z-index: var(--z-popover);
  max-width: calc(100vw - var(--space-4));
  max-height: calc(100vh - var(--space-4));
  overflow-y: auto;
  padding: var(--space-1);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-lg);
  animation: ui-dropdown-in var(--duration-fast) var(--ease-enter);
}

@keyframes ui-dropdown-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.ui-dropdown__item {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: var(--control-height);
  padding: 0 var(--space-3);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-1);
  font-size: var(--text-sm);
  line-height: var(--line-normal);
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  transition:
    color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.ui-dropdown__item:hover:not([aria-disabled='true']) {
  background: var(--hover-bg);
}

.ui-dropdown__item:focus-visible {
  background: var(--hover-bg);
  box-shadow: var(--focus-ring);
}

.ui-dropdown__item--danger {
  color: var(--danger);
}

.ui-dropdown__item--danger:hover:not([aria-disabled='true']),
.ui-dropdown__item--danger:focus-visible {
  background: var(--danger-weak);
}

.ui-dropdown__item[aria-disabled='true'] {
  color: var(--muted);
  cursor: not-allowed;
}
</style>
