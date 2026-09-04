<!-- 选项卡原语：tablist/tab/tabpanel ARIA 语义，方向键左右循环、Home/End 跳转，自动激活；
     受控 modelValue（v-model）让调用方把选中值映射到路由 query 等深链来源，modelValue 缺失或
     指向禁用/不存在的 tab 时回退展示第一个可用 tab，但不代调用方发事件。
     tab 面板一次性渲染并完整关联 aria-controls / aria-labelledby，重内容可在 panel slot 里
     用 active 标志懒挂载。 -->
<template>
  <div class="ui-tabs">
    <div ref="listRef" class="ui-tabs__list" role="tablist" :aria-label="label" @keydown="onKeydown">
      <button
        v-for="(tab, index) in tabs"
        :key="tabKey(tab)"
        :id="tabId(index)"
        type="button"
        role="tab"
        class="ui-tabs__tab"
        :class="{ 'ui-tabs__tab--active': isActive(tab) }"
        :aria-selected="isActive(tab) ? 'true' : 'false'"
        :aria-controls="panelId(index)"
        :tabindex="isActive(tab) ? 0 : -1"
        :disabled="tab.disabled"
        @click="select(tab)"
      >
        <slot name="tab" :tab="tab" :active="isActive(tab)">{{ tab.label }}</slot>
      </button>
    </div>
    <div
      v-for="(tab, index) in tabs"
      v-show="isActive(tab)"
      :key="tabKey(tab)"
      :id="panelId(index)"
      role="tabpanel"
      class="ui-tabs__panel"
      tabindex="0"
      :aria-labelledby="tabId(index)"
    >
      <slot name="panel" :tab="tab" :active="isActive(tab)" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, useId } from 'vue'

const props = defineProps({
  tabs: { type: Array, default: () => [] }, // [{ value, label, disabled? }]
  modelValue: { type: [String, Number], default: '' },
  label: { type: String, required: true } // tablist 的可访问名称
})

const emit = defineEmits(['update:modelValue'])

const uid = useId()
const tabId = (index) => `ui-tabs-${uid}-tab-${index}`
const panelId = (index) => `ui-tabs-${uid}-panel-${index}`

const listRef = ref(null)

const enabledTabs = computed(() => props.tabs.filter((tab) => !tab.disabled))

const activeValue = computed(() => {
  const exact = props.tabs.find((tab) => !tab.disabled && tab.value === props.modelValue)
  return (exact || enabledTabs.value[0] || {}).value
})

function tabKey(tab) {
  return String(tab.value)
}

function isActive(tab) {
  return tab.value === activeValue.value
}

function select(tab) {
  if (!tab || tab.disabled || tab.value === activeValue.value) return
  emit('update:modelValue', tab.value)
}

function focusTab(tab) {
  const list = listRef.value
  if (!list) return
  const buttons = [...list.querySelectorAll('[role="tab"]')]
  buttons[props.tabs.indexOf(tab)]?.focus()
}

function onKeydown(event) {
  const key = event?.key
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(key)) return
  const enabled = enabledTabs.value
  if (!enabled.length) return
  event.preventDefault()
  const current = enabled.findIndex((tab) => tab.value === activeValue.value)
  const from = current === -1 ? 0 : current
  let next
  if (key === 'Home') next = enabled[0]
  else if (key === 'End') next = enabled[enabled.length - 1]
  else if (key === 'ArrowRight') next = enabled[(from + 1) % enabled.length]
  else next = enabled[(from - 1 + enabled.length) % enabled.length]
  select(next)
  focusTab(next)
}
</script>

<style scoped>
.ui-tabs__list {
  display: flex;
  gap: var(--space-1);
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
}

.ui-tabs__tab {
  flex: none;
  min-height: var(--control-height);
  padding: 0 var(--space-3);
  border: 0;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  background: transparent;
  color: var(--text-2);
  font-size: var(--text-sm);
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.ui-tabs__tab:hover:not(:disabled) {
  color: var(--text-1);
  background: var(--hover-bg);
}

.ui-tabs__tab:focus-visible {
  box-shadow: var(--focus-ring);
}

.ui-tabs__tab--active {
  color: var(--accent-text);
  border-bottom-color: var(--accent);
}

.ui-tabs__tab:disabled {
  color: var(--muted);
  cursor: not-allowed;
}

.ui-tabs__panel {
  padding-top: var(--space-4);
}

.ui-tabs__panel:focus-visible {
  border-radius: var(--radius-md);
  box-shadow: var(--focus-ring);
}
</style>
