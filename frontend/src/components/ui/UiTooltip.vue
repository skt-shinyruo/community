<!-- 文字提示：hover/focus 触发，Escape 关闭，视口边界自动翻转与夹取。
     仅作补充说明（trigger 保留自己的可访问名称），任何操作不得依赖 tooltip 才能完成。 -->
<template>
  <span
    ref="rootRef"
    class="ui-tooltip"
    @mouseenter="show"
    @mouseleave="hide"
    @focusin="show"
    @focusout="hide"
    @keydown.esc="hide"
  >
    <span ref="triggerRef" class="ui-tooltip__trigger" :aria-describedby="visible ? tooltipId : undefined">
      <slot />
    </span>
    <Teleport to="body">
      <div
        v-if="visible"
        :id="tooltipId"
        ref="bubbleRef"
        role="tooltip"
        class="ui-tooltip__bubble"
        :class="`ui-tooltip__bubble--${resolvedPlacement}`"
        :style="bubbleStyle"
      >
        <slot name="content">{{ text }}</slot>
      </div>
    </Teleport>
  </span>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, useId } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
  placement: { type: String, default: 'top' } // top | bottom，视口空间不足时自动翻转
})

const GAP = 6
const VIEWPORT_MARGIN = 8

const tooltipId = `ui-tooltip-${useId()}`
const rootRef = ref(null)
const triggerRef = ref(null)
const bubbleRef = ref(null)
const visible = ref(false)
const resolvedPlacement = ref('top')
const bubbleStyle = ref({ visibility: 'hidden' })

function preferredPlacement() {
  return props.placement === 'bottom' ? 'bottom' : 'top'
}

function updatePosition() {
  const trigger = triggerRef.value
  const bubble = bubbleRef.value
  if (!trigger || !bubble) return
  const triggerRect = trigger.getBoundingClientRect()
  const bubbleRect = bubble.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight

  let placement = preferredPlacement()
  const spaceAbove = triggerRect.top
  const spaceBelow = viewportHeight - triggerRect.bottom
  if (placement === 'top' && spaceAbove < bubbleRect.height + GAP && spaceBelow > spaceAbove) {
    placement = 'bottom'
  } else if (placement === 'bottom' && spaceBelow < bubbleRect.height + GAP && spaceAbove > spaceBelow) {
    placement = 'top'
  }
  resolvedPlacement.value = placement

  const centeredLeft = triggerRect.left + triggerRect.width / 2 - bubbleRect.width / 2
  const maxLeft = Math.max(VIEWPORT_MARGIN, viewportWidth - bubbleRect.width - VIEWPORT_MARGIN)
  const left = Math.min(Math.max(centeredLeft, VIEWPORT_MARGIN), maxLeft)
  const aboveTop = triggerRect.top - bubbleRect.height - GAP
  const belowTop = triggerRect.bottom + GAP
  const maxTop = Math.max(VIEWPORT_MARGIN, viewportHeight - bubbleRect.height - VIEWPORT_MARGIN)
  const top = Math.min(Math.max(placement === 'top' ? aboveTop : belowTop, VIEWPORT_MARGIN), maxTop)

  bubbleStyle.value = { top: `${Math.round(top)}px`, left: `${Math.round(left)}px`, visibility: 'visible' }
}

async function show() {
  if (visible.value) return
  resolvedPlacement.value = preferredPlacement()
  bubbleStyle.value = { visibility: 'hidden' }
  visible.value = true
  await nextTick()
  updatePosition()
  window.addEventListener('resize', updatePosition)
  window.addEventListener('scroll', updatePosition, { capture: true, passive: true })
}

function hide() {
  if (!visible.value) return
  visible.value = false
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, { capture: true })
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, { capture: true })
})
</script>

<style scoped>
.ui-tooltip {
  display: inline-block;
}

.ui-tooltip__bubble {
  position: fixed;
  top: 0;
  left: 0;
  z-index: var(--z-popover);
  max-width: 240px;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--text-1);
  color: var(--bg);
  font-size: var(--text-xs);
  line-height: var(--line-normal);
  box-shadow: var(--shadow-md);
  pointer-events: none;
  animation: ui-tooltip-in var(--duration-fast) var(--ease-enter);
}

@keyframes ui-tooltip-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}
</style>
