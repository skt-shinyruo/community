<!-- 单选下拉原语：APG select-only combobox/listbox 语义。combobox 按钮始终持有 DOM 焦点，
     打开后以 aria-activedescendant 指向活动选项；click 与 Enter/Space/↓/↑ 打开（↓ 定位已选或
     首个可用项，无已选时 ↑ 定位末项），打开后 ↑/↓ 移动活动项（不循环）、Home/End 跳转首尾、
     Enter/Space 选中并关闭；Escape 关闭且不改动选中，选中、Escape 与清除后焦点回到 combobox。
     关闭态直接键入字符按标签前缀选中匹配项，打开态键入只移动活动项（原生 select 的
     typeahead 语义，不是搜索输入框）；不支持多选。
     浮层 teleport 到 body，与 UiDropdown 同一定位策略：视口空间不足时翻转并夹取在视口内。
     在 UiField 内自动继承 label 关联、描述与校验状态（required 映射为 aria-required）。 -->
<template>
  <span ref="rootRef" class="ui-select" :class="{ 'ui-select--open': open, 'ui-select--clearable': showClear }">
    <span v-if="!hasFieldLabel && label" :id="ownLabelId" class="sr-only">{{ label }}</span>
    <button
      v-bind="controlAttrs"
      ref="triggerRef"
      type="button"
      role="combobox"
      class="ui-select__trigger"
      aria-haspopup="listbox"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="open ? listboxId : undefined"
      :aria-activedescendant="activeDescendant"
      :aria-labelledby="labelledBy"
      :disabled="disabled"
      @click="onTriggerClick"
      @keydown="onTriggerKeydown"
    >
      <span :id="valueId" class="ui-select__value" :class="{ 'ui-select__value--placeholder': !selectedOption }">
        <slot :option="selectedOption">{{ displayLabel }}</slot>
      </span>
      <ChevronDown :size="16" aria-hidden="true" class="ui-select__chevron" />
    </button>
    <button
      v-if="showClear"
      type="button"
      class="ui-select__clear"
      :aria-label="clearLabel"
      @click="onClear"
    >
      <X :size="14" aria-hidden="true" />
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        ref="listboxRef"
        :id="listboxId"
        role="listbox"
        class="ui-select__listbox"
        :class="`ui-select__listbox--${resolvedPlacement}`"
        :aria-labelledby="labelSourceId || undefined"
        :aria-busy="loading ? 'true' : undefined"
        :style="listboxStyle"
        tabindex="-1"
        @mousedown.prevent
      >
        <div v-if="loading" class="ui-select__status" role="status">
          <span class="ui-select__spinner" aria-hidden="true" />
          正在加载选项
        </div>
        <div v-else-if="!options.length" class="ui-select__empty" role="option" aria-disabled="true">暂无可选项</div>
        <template v-else>
          <div
            v-for="(option, index) in options"
            :key="optionKey(option)"
            :id="optionId(index)"
            role="option"
            class="ui-select__option"
            :class="{
              'ui-select__option--active': index === activeIndex,
              'ui-select__option--selected': isSelected(option)
            }"
            :aria-selected="isSelected(option) ? 'true' : 'false'"
            :aria-disabled="option.disabled ? 'true' : undefined"
            @click="commitOption(option)"
            @mouseover="onOptionHover(option, index)"
          >
            <slot name="option" :option="option" :active="index === activeIndex" :selected="isSelected(option)">
              {{ option.label }}
            </slot>
          </div>
        </template>
      </div>
    </Teleport>
  </span>
</template>

<script setup>
import { computed, inject, nextTick, onBeforeUnmount, ref, useAttrs, useId } from 'vue'
import { ChevronDown, X } from 'lucide-vue-next'
import { uiFieldContextKey, useFieldControlAttrs } from './fieldContext'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  options: { type: Array, default: () => [] }, // [{ value, label, disabled? }]
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, default: '' }, // 独立使用时 combobox 的可访问名称；UiField 内由字段 label 提供
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false },
  clearable: { type: Boolean, default: false },
  clearLabel: { type: String, default: '清除选择' },
  loading: { type: Boolean, default: false },
  placement: { type: String, default: 'bottom' } // bottom | top，视口空间不足时自动翻转
})

const emit = defineEmits(['update:modelValue'])

const GAP = 4
const VIEWPORT_MARGIN = 8
const TYPEAHEAD_RESET_MS = 500

const uid = useId()
const listboxId = `ui-select-listbox-${uid}`
const ownLabelId = `ui-select-label-${uid}`
const valueId = `ui-select-value-${uid}`

const field = inject(uiFieldContextKey, null)
const controlAttrs = useFieldControlAttrs(useAttrs(), { nativeRequired: false })

const rootRef = ref(null)
const triggerRef = ref(null)
const listboxRef = ref(null)
const open = ref(false)
const activeIndex = ref(-1)
const resolvedPlacement = ref('bottom')
const listboxStyle = ref({ visibility: 'hidden' })

let typeaheadBuffer = ''
let typeaheadTimer = null

const hasFieldLabel = computed(() => Boolean(field))
const labelSourceId = computed(() => field?.labelId || (props.label ? ownLabelId : ''))
// 名称按 APG select-only combobox 组合「label + 当前值」。
const labelledBy = computed(() => [labelSourceId.value, valueId].filter(Boolean).join(' '))

const selectedOption = computed(() => props.options.find((option) => option.value === props.modelValue) || null)
const displayLabel = computed(() => selectedOption.value?.label ?? props.placeholder)

const hasValue = computed(() => props.modelValue !== '' && props.modelValue !== null && props.modelValue !== undefined)
const showClear = computed(() => props.clearable && !props.disabled && hasValue.value)

const activeDescendant = computed(() =>
  open.value && !props.loading && activeIndex.value >= 0 ? optionId(activeIndex.value) : undefined
)

function optionId(index) {
  return `ui-select-option-${uid}-${index}`
}

function optionKey(option) {
  return String(option.value)
}

function isSelected(option) {
  return option.value === props.modelValue
}

function preferredPlacement() {
  return props.placement === 'top' ? 'top' : 'bottom'
}

function enabledIndexes() {
  return props.options.map((option, index) => (option.disabled ? -1 : index)).filter((index) => index !== -1)
}

// 打开时的活动项：已选且可用的选项优先，否则按方向取首/末可用项。
function initialActiveIndex(prefer) {
  const enabled = enabledIndexes()
  if (!enabled.length) return -1
  const selectedIndex = props.options.findIndex((option) => !option.disabled && isSelected(option))
  if (selectedIndex !== -1) return selectedIndex
  return prefer === 'last' ? enabled[enabled.length - 1] : enabled[0]
}

function scrollActiveIntoView() {
  const listbox = listboxRef.value
  if (!listbox || activeIndex.value < 0) return
  listbox.querySelectorAll('[role="option"]')[activeIndex.value]?.scrollIntoView?.({ block: 'nearest' })
}

function moveActive(step) {
  const enabled = enabledIndexes()
  if (!enabled.length) return
  const position = enabled.indexOf(activeIndex.value)
  const nextPosition =
    position === -1 ? (step > 0 ? 0 : enabled.length - 1) : Math.min(Math.max(position + step, 0), enabled.length - 1)
  activeIndex.value = enabled[nextPosition]
  scrollActiveIntoView()
}

function focusEdge(which) {
  const enabled = enabledIndexes()
  if (!enabled.length) return
  activeIndex.value = which === 'last' ? enabled[enabled.length - 1] : enabled[0]
  scrollActiveIntoView()
}

// 从参照项之后按标签前缀循环查找可用选项；禁用项不参与匹配。
function findByPrefix(query, referenceIndex) {
  const enabled = enabledIndexes()
  if (!enabled.length || !query) return -1
  const startPosition = enabled.indexOf(referenceIndex)
  for (let offset = 1; offset <= enabled.length; offset += 1) {
    const position = (startPosition + offset + enabled.length) % enabled.length
    const index = enabled[position]
    const label = String(props.options[index]?.label ?? '').trim().toLowerCase()
    if (label.startsWith(query)) return index
  }
  return -1
}

function onTypeahead(char) {
  if (props.loading) return
  clearTimeout(typeaheadTimer)
  typeaheadTimer = setTimeout(() => {
    typeaheadBuffer = ''
  }, TYPEAHEAD_RESET_MS)
  typeaheadBuffer += char
  // 连续重复同一字符时按单字符在匹配项之间循环（原生 select 行为）。
  const repeated = typeaheadBuffer.length > 1 && [...typeaheadBuffer].every((c) => c === typeaheadBuffer[0])
  const reference = open.value
    ? activeIndex.value
    : props.options.findIndex((option) => !option.disabled && isSelected(option))
  let query = (repeated ? typeaheadBuffer[0] : typeaheadBuffer).toLowerCase()
  let index = findByPrefix(query, reference)
  if (index === -1 && typeaheadBuffer.length > 1 && !repeated) {
    // 整串无匹配时退化为最新字符重新匹配（原生 select 手感）。
    typeaheadBuffer = char
    query = char.toLowerCase()
    index = findByPrefix(query, reference)
  }
  if (index === -1) return
  if (open.value) {
    activeIndex.value = index
    scrollActiveIntoView()
  } else if (!isSelected(props.options[index])) {
    emit('update:modelValue', props.options[index].value)
  }
}

function updatePosition() {
  const trigger = triggerRef.value
  const listbox = listboxRef.value
  if (!trigger || !listbox) return
  const triggerRect = trigger.getBoundingClientRect()
  const listboxRect = listbox.getBoundingClientRect()
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight

  let placement = preferredPlacement()
  const spaceAbove = triggerRect.top
  const spaceBelow = viewportHeight - triggerRect.bottom
  if (placement === 'bottom' && spaceBelow < listboxRect.height + GAP && spaceAbove > spaceBelow) {
    placement = 'top'
  } else if (placement === 'top' && spaceAbove < listboxRect.height + GAP && spaceBelow > spaceAbove) {
    placement = 'bottom'
  }
  resolvedPlacement.value = placement

  const maxLeft = Math.max(VIEWPORT_MARGIN, viewportWidth - listboxRect.width - VIEWPORT_MARGIN)
  const left = Math.min(Math.max(triggerRect.left, VIEWPORT_MARGIN), maxLeft)
  const belowTop = triggerRect.bottom + GAP
  const aboveTop = triggerRect.top - listboxRect.height - GAP
  const maxTop = Math.max(VIEWPORT_MARGIN, viewportHeight - listboxRect.height - VIEWPORT_MARGIN)
  const top = Math.min(Math.max(placement === 'bottom' ? belowTop : aboveTop, VIEWPORT_MARGIN), maxTop)

  listboxStyle.value = {
    top: `${Math.round(top)}px`,
    left: `${Math.round(left)}px`,
    minWidth: `${Math.round(triggerRect.width)}px`,
    visibility: 'visible'
  }
}

function onDocumentPointerdown(event) {
  const target = event?.target
  if (rootRef.value?.contains(target) || listboxRef.value?.contains(target)) return
  closeListbox()
}

async function openListbox(prefer = 'first') {
  if (props.disabled || open.value) return
  resolvedPlacement.value = preferredPlacement()
  listboxStyle.value = { visibility: 'hidden' }
  activeIndex.value = props.loading ? -1 : initialActiveIndex(prefer)
  open.value = true
  await nextTick()
  updatePosition()
  scrollActiveIntoView()
  triggerRef.value?.focus()
  window.addEventListener('resize', updatePosition)
  window.addEventListener('scroll', updatePosition, { capture: true, passive: true })
  document.addEventListener('pointerdown', onDocumentPointerdown, true)
}

function closeListbox({ returnFocus = false } = {}) {
  if (!open.value) return
  open.value = false
  activeIndex.value = -1
  typeaheadBuffer = ''
  clearTimeout(typeaheadTimer)
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, { capture: true })
  document.removeEventListener('pointerdown', onDocumentPointerdown, true)
  if (returnFocus) triggerRef.value?.focus()
}

function commitOption(option) {
  if (!option || option.disabled) return
  if (!isSelected(option)) emit('update:modelValue', option.value)
  closeListbox({ returnFocus: true })
}

function onClear() {
  if (props.disabled || !hasValue.value) return
  emit('update:modelValue', '')
  closeListbox()
  triggerRef.value?.focus()
}

function onTriggerClick() {
  if (props.disabled) return
  if (open.value) closeListbox()
  else openListbox('first')
}

function onOptionHover(option, index) {
  if (option.disabled) return
  activeIndex.value = index
}

function isPrintableChar(event) {
  return event?.key?.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey
}

function onTriggerKeydown(event) {
  if (props.disabled) return
  const key = event?.key
  if (key === 'Escape') {
    if (open.value) {
      event.preventDefault()
      event.stopPropagation()
      closeListbox({ returnFocus: true })
    }
    return
  }
  if (key === 'Tab') {
    // 不拦截 Tab：收起浮层后让焦点按自然顺序前进。
    closeListbox()
    return
  }
  if (props.loading && open.value) return
  if (key === 'ArrowDown') {
    event.preventDefault()
    if (open.value) moveActive(1)
    else openListbox('first')
  } else if (key === 'ArrowUp') {
    event.preventDefault()
    if (open.value) moveActive(-1)
    else openListbox('last')
  } else if (key === 'Home') {
    if (!open.value) return
    event.preventDefault()
    focusEdge('first')
  } else if (key === 'End') {
    if (!open.value) return
    event.preventDefault()
    focusEdge('last')
  } else if (key === 'Enter' || key === ' ') {
    // preventDefault 抑制原生 click / 页面滚动，避免与 click 处理器重复切换。
    event.preventDefault()
    if (open.value) commitOption(props.options[activeIndex.value])
    else openListbox('first')
  } else if (isPrintableChar(event)) {
    onTypeahead(key.toLowerCase())
  }
}

onBeforeUnmount(() => {
  clearTimeout(typeaheadTimer)
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, { capture: true })
  document.removeEventListener('pointerdown', onDocumentPointerdown, true)
})
</script>

<style scoped>
.ui-select {
  position: relative;
  display: inline-block;
  width: 100%;
}

.ui-select__trigger {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  height: var(--control-height);
  padding: 0 var(--control-padding-x);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  outline: none;
  background: var(--bg);
  color: var(--text-1);
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.ui-select__trigger:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.ui-select__trigger:focus-visible {
  box-shadow: var(--focus-ring);
}

.ui-select__trigger[aria-invalid='true'] {
  border-color: var(--danger);
}

.ui-select__trigger[aria-invalid='true']:focus-visible {
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--danger) 24%, transparent);
}

.ui-select__trigger:disabled {
  background: var(--surface-2);
  color: var(--muted);
  cursor: not-allowed;
}

.ui-select__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ui-select--clearable .ui-select__value {
  padding-right: var(--space-5);
}

.ui-select__value--placeholder {
  color: var(--muted);
}

.ui-select__chevron {
  flex: none;
  color: var(--text-3);
  transition: transform var(--duration-fast) var(--ease-standard);
}

.ui-select--open .ui-select__chevron {
  transform: rotate(180deg);
}

.ui-select__trigger:disabled .ui-select__chevron {
  color: var(--muted);
}

.ui-select__clear {
  position: absolute;
  top: 50%;
  right: var(--space-7);
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
  transition:
    color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.ui-select__clear:hover {
  color: var(--text-1);
  background: var(--hover-bg);
}

.ui-select__clear:focus-visible {
  box-shadow: var(--focus-ring);
}

.ui-select__listbox {
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
  animation: ui-select-in var(--duration-fast) var(--ease-enter);
}

@keyframes ui-select-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.ui-select__option {
  display: flex;
  align-items: center;
  min-height: var(--control-height);
  padding: 0 var(--space-3);
  border-radius: var(--radius-sm);
  color: var(--text-1);
  font-size: var(--text-sm);
  line-height: var(--line-normal);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  transition:
    color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.ui-select__option--active {
  background: var(--hover-bg);
}

.ui-select__option--selected {
  color: var(--accent-text);
  font-weight: 600;
}

.ui-select__option[aria-disabled='true'] {
  color: var(--muted);
  cursor: not-allowed;
}

.ui-select__option--active[aria-disabled='true'] {
  background: transparent;
}

.ui-select__status,
.ui-select__empty {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: var(--control-height);
  padding: 0 var(--space-3);
  color: var(--text-3);
  font-size: var(--text-sm);
  white-space: nowrap;
}

.ui-select__spinner {
  flex: none;
  width: 14px;
  height: 14px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: var(--radius-full);
  animation: ui-select-spin var(--duration-slower) linear infinite;
}

@keyframes ui-select-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
