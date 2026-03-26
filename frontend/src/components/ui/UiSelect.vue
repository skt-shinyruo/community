<template>
  <div class="ui-select" :class="{ 'is-open': open, 'is-disabled': disabled }">
    <input v-if="name" type="hidden" :name="name" :value="hiddenValue" />

    <button
      :id="triggerId"
      ref="triggerRef"
      type="button"
      class="input ui-select-trigger"
      :disabled="disabled"
      aria-haspopup="listbox"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="listboxId"
      :aria-label="resolvedAriaLabel"
      :aria-activedescendant="activeOptionId"
      @click="toggleOpen"
      @keydown="onTriggerKeydown"
    >
      <span class="ui-select-label">{{ currentLabel }}</span>
      <span class="ui-select-caret" aria-hidden="true">▾</span>
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        :id="listboxId"
        ref="panelRef"
        class="ui-select-panel"
        role="listbox"
        :aria-labelledby="triggerId"
        :style="panelStyle"
      >
        <button
          v-for="(option, index) in options"
          :id="optionId(index)"
          :key="String(option.value)"
          type="button"
          class="ui-select-option"
          :class="{
            'is-active': index === activeIndex,
            'is-selected': isSelected(option),
            'is-disabled': !!option.disabled
          }"
          :data-value="String(option.value)"
          :disabled="!!option.disabled"
          tabindex="-1"
          role="option"
          :aria-selected="isSelected(option) ? 'true' : 'false'"
          @pointerdown.prevent
          @click="selectOption(option)"
        >
          {{ option.label }}
        </button>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  name: { type: String, default: '' },
  ariaLabel: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const activeIndex = ref(-1)
const triggerRef = ref(null)
const panelRef = ref(null)
const panelStyle = ref({})
const pendingFocusRestore = ref(false)
const uid = useId()

const triggerId = `ui-select-trigger-${uid}`
const listboxId = `ui-select-listbox-${uid}`

const hasComparableModelValue = computed(
  () => props.modelValue !== null && props.modelValue !== undefined
)

const hiddenValue = computed(() => props.modelValue ?? '')
const resolvedAriaLabel = computed(() => props.ariaLabel || undefined)
const activeOptionId = computed(() =>
  open.value && activeIndex.value >= 0 ? optionId(activeIndex.value) : undefined
)

const currentOption = computed(() => {
  if (!hasComparableModelValue.value) return null
  return props.options.find((option) => isSelected(option)) ?? null
})

const currentLabel = computed(() => currentOption.value?.label ?? props.placeholder)

function isSelected(option) {
  if (!hasComparableModelValue.value) return false
  return String(option?.value) === String(props.modelValue)
}

function optionId(index) {
  return `ui-select-option-${uid}-${index}`
}

function isEnabledIndex(index) {
  return index >= 0 && index < props.options.length && !props.options[index]?.disabled
}

function getFirstEnabledIndex() {
  return props.options.findIndex((option) => !option?.disabled)
}

function getSelectedIndex() {
  if (!hasComparableModelValue.value) return -1
  return props.options.findIndex((option) => isSelected(option))
}

function getInitialActiveIndex() {
  const selectedIndex = getSelectedIndex()

  if (isEnabledIndex(selectedIndex)) return selectedIndex

  return getFirstEnabledIndex()
}

function findNextEnabledIndex(startIndex, direction) {
  let index = startIndex + direction

  while (index >= 0 && index < props.options.length) {
    if (!props.options[index]?.disabled) return index
    index += direction
  }

  return -1
}

function focusTrigger() {
  triggerRef.value?.focus?.()
}

function syncPanelPosition() {
  if (typeof window === 'undefined') return

  const rect = triggerRef.value?.getBoundingClientRect?.()
  if (!rect) return

  const panelHeight = panelRef.value?.offsetHeight || 0
  const viewportHeight = window.innerHeight || 0
  const spaceBelow = viewportHeight - rect.bottom
  const top =
    spaceBelow >= Math.min(panelHeight || 240, 240)
      ? rect.bottom + 6
      : Math.max(8, rect.top - panelHeight - 6)

  panelStyle.value = {
    position: 'fixed',
    top: `${top}px`,
    left: `${rect.left}px`,
    minWidth: `${rect.width}px`
  }
}

function openMenu() {
  if (props.disabled || open.value) return

  activeIndex.value = getInitialActiveIndex()
  pendingFocusRestore.value = false
  open.value = true
}

function closeMenu({ restoreFocus = false } = {}) {
  pendingFocusRestore.value = restoreFocus

  if (!open.value) {
    if (restoreFocus) {
      void nextTick(() => {
        focusTrigger()
        pendingFocusRestore.value = false
      })
    }
    return
  }

  open.value = false
  activeIndex.value = -1
  panelStyle.value = {}
}

function toggleOpen() {
  if (open.value) {
    closeMenu()
    return
  }

  openMenu()
}

function moveActive(direction) {
  const currentIndex = isEnabledIndex(activeIndex.value) ? activeIndex.value : getInitialActiveIndex()

  if (currentIndex === -1) return

  const nextIndex = findNextEnabledIndex(currentIndex, direction)
  activeIndex.value = nextIndex === -1 ? currentIndex : nextIndex
}

function selectActiveOption({ restoreFocus = true } = {}) {
  if (!isEnabledIndex(activeIndex.value)) return
  selectOption(props.options[activeIndex.value], { restoreFocus })
}

function onTriggerKeydown(event) {
  if (props.disabled) return

  const key = String(event?.key || '')

  if (key === 'ArrowDown') {
    event.preventDefault()

    if (!open.value) {
      openMenu()
      return
    }

    moveActive(1)
    return
  }

  if (key === 'ArrowUp') {
    event.preventDefault()

    if (!open.value) {
      openMenu()
      return
    }

    moveActive(-1)
    return
  }

  if (key === 'Enter') {
    event.preventDefault()

    if (!open.value) {
      openMenu()
      return
    }

    selectActiveOption()
    return
  }

  if (key === ' ' || key === 'Spacebar') {
    event.preventDefault()

    if (!open.value) openMenu()
    return
  }

  if (key === 'Tab' && open.value) {
    closeMenu()
    return
  }

  if (key === 'Escape' && open.value) {
    event.preventDefault()
    closeMenu({ restoreFocus: true })
  }
}

function onPointerdownOutside(event) {
  if (!open.value) return

  const target = event?.target

  if (triggerRef.value?.contains?.(target)) return
  if (panelRef.value?.contains?.(target)) return

  closeMenu()
}

function addOpenListeners() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return

  document.addEventListener('pointerdown', onPointerdownOutside, true)
  window.addEventListener('resize', syncPanelPosition)
  window.addEventListener('scroll', syncPanelPosition, true)
}

function removeOpenListeners() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return

  document.removeEventListener('pointerdown', onPointerdownOutside, true)
  window.removeEventListener('resize', syncPanelPosition)
  window.removeEventListener('scroll', syncPanelPosition, true)
}

function selectOption(option, { restoreFocus = false } = {}) {
  if (props.disabled || option?.disabled) return

  closeMenu({ restoreFocus })
  emit('update:modelValue', option?.value)
  emit('change', option?.value)
}

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) closeMenu()
  }
)

watch(open, async (isOpen) => {
  if (isOpen) {
    addOpenListeners()
    await nextTick()
    syncPanelPosition()
    return
  }

  removeOpenListeners()
  panelStyle.value = {}

  if (pendingFocusRestore.value) {
    await nextTick()
    focusTrigger()
  }

  pendingFocusRestore.value = false
})

watch(
  [() => props.options, () => props.modelValue],
  () => {
    if (!open.value) return
    if (!isEnabledIndex(activeIndex.value)) activeIndex.value = getInitialActiveIndex()
    void nextTick(syncPanelPosition)
  },
  { deep: true }
)

onBeforeUnmount(() => {
  removeOpenListeners()
})
</script>
