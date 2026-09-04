<!-- 首载骨架屏：list / card / detail 三种结构占位，收敛散落的裸加载文本。
     role="status" + sr-only 标签向辅助技术播报加载状态，视觉块对辅助技术隐藏。 -->
<template>
  <div class="ui-skeleton" :class="`ui-skeleton--${safeVariant}`" role="status">
    <span class="sr-only">{{ label }}</span>
    <template v-if="safeVariant === 'card'">
      <div class="ui-skeleton__card" aria-hidden="true">
        <div class="ui-skeleton__meta">
          <div class="ui-skeleton__block ui-skeleton__pill"></div>
          <div class="ui-skeleton__block ui-skeleton__pill ui-skeleton__pill--sm"></div>
        </div>
        <div class="ui-skeleton__block ui-skeleton__title"></div>
        <div class="ui-skeleton__block ui-skeleton__line"></div>
        <div class="ui-skeleton__block ui-skeleton__line ui-skeleton__line--short"></div>
        <div class="ui-skeleton__block ui-skeleton__foot"></div>
      </div>
    </template>
    <template v-else-if="safeVariant === 'detail'">
      <div class="ui-skeleton__detail" aria-hidden="true">
        <div class="ui-skeleton__block ui-skeleton__title ui-skeleton__title--lg"></div>
        <div class="ui-skeleton__block ui-skeleton__line ui-skeleton__line--meta"></div>
        <div class="ui-skeleton__block ui-skeleton__line"></div>
        <div class="ui-skeleton__block ui-skeleton__line"></div>
        <div class="ui-skeleton__block ui-skeleton__line ui-skeleton__line--short"></div>
      </div>
    </template>
    <template v-else>
      <div v-for="row in rowCount" :key="row" class="ui-skeleton__row" aria-hidden="true">
        <div class="ui-skeleton__block ui-skeleton__thumb"></div>
        <div class="ui-skeleton__lines">
          <div class="ui-skeleton__block ui-skeleton__line ui-skeleton__line--title"></div>
          <div class="ui-skeleton__block ui-skeleton__line ui-skeleton__line--short"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'list' }, // list | card | detail
  rows: { type: Number, default: 3 },
  label: { type: String, default: '加载中' }
})

const VARIANTS = ['list', 'card', 'detail']
const safeVariant = computed(() => (VARIANTS.includes(props.variant) ? props.variant : 'list'))
const rowCount = computed(() => Math.max(1, Math.min(20, Math.trunc(props.rows) || 1)))
</script>

<style scoped>
.ui-skeleton {
  display: grid;
  gap: var(--space-3);
}

.ui-skeleton__block {
  background: linear-gradient(90deg, var(--surface-2) 25%, var(--border) 37%, var(--surface-2) 63%);
  background-size: 200% 100%;
  animation: ui-skeleton-shimmer 1.5s var(--ease-standard) infinite;
  border-radius: var(--radius-sm);
}

@keyframes ui-skeleton-shimmer {
  0% {
    background-position: -200% 0;
  }

  100% {
    background-position: 200% 0;
  }
}

/* list：缩略块 + 两行文本的行占位 */
.ui-skeleton__row {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

.ui-skeleton__thumb {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: var(--radius-md);
}

.ui-skeleton__lines {
  flex: 1;
  display: grid;
  gap: var(--space-2);
}

.ui-skeleton__line {
  height: 1em;
}

.ui-skeleton__line--title {
  width: 60%;
}

.ui-skeleton__line--meta {
  width: 40%;
}

.ui-skeleton__line--short {
  width: 35%;
}

/* card：消费流卡片占位 */
.ui-skeleton__card {
  display: grid;
  gap: var(--space-3);
  padding: var(--card-padding);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.ui-skeleton__meta {
  display: flex;
  gap: var(--space-2);
}

.ui-skeleton__pill {
  width: 56px;
  height: 16px;
  border-radius: var(--radius-full);
}

.ui-skeleton__pill--sm {
  width: 36px;
}

.ui-skeleton__title {
  height: 1.2em;
  width: 70%;
}

.ui-skeleton__title--lg {
  height: 1.4em;
  width: 55%;
}

.ui-skeleton__foot {
  height: 1em;
  width: 45%;
}

/* detail：详情首屏段落占位 */
.ui-skeleton__detail {
  display: grid;
  gap: var(--space-3);
}
</style>
