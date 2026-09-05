<!-- 裁剪版表格原语（规范 5.2，波次 9 首次交付）：只承担语义 table、统一样式和排序钩子。
     排序是受控的——视图持有 sortKey/sortDirection 并响应 sort 事件自行重排 rows；
     不提供虚拟滚动、冻结列或单元格编辑，出现这些需求时再按规范重新评估按需表格组件。
     列头排序控件是原生 button（click 与 Enter/Space 激活），aria-sort 只落在当前排序列；
     横向空间不足时由外层容器滚动，不撑破页面布局。 -->
<template>
  <div class="ui-table">
    <table class="ui-table__table">
      <caption v-if="caption" class="sr-only">{{ caption }}</caption>
      <thead>
        <tr>
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            :class="{ 'ui-table__cell--right': column.align === 'right' }"
            :aria-sort="ariaSort(column)"
          >
            <button
              v-if="column.sortable"
              type="button"
              class="ui-table__sort"
              @click="$emit('sort', column.key)"
            >
              <span>{{ column.label }}</span>
              <ArrowUp v-if="isSorted(column, 'asc')" :size="14" aria-hidden="true" />
              <ArrowDown v-else-if="isSorted(column, 'desc')" :size="14" aria-hidden="true" />
              <ChevronsUpDown v-else :size="14" aria-hidden="true" class="ui-table__sort-hint" />
            </button>
            <template v-else>{{ column.label }}</template>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, rowIndex) in rows" :key="rowKeyOf(row, rowIndex)">
          <td
            v-for="column in columns"
            :key="column.key"
            :class="{ 'ui-table__cell--right': column.align === 'right' }"
          >
            <slot :name="`cell-${column.key}`" :row="row" :column="column">{{ cellText(row, column) }}</slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-vue-next'

const props = defineProps({
  columns: { type: Array, default: () => [] }, // [{ key, label, sortable?, align?: 'right' }]
  rows: { type: Array, default: () => [] },
  rowKey: { type: [String, Function], default: 'id' },
  caption: { type: String, default: '' },
  sortKey: { type: String, default: '' },
  sortDirection: { type: String, default: 'asc' } // asc | desc
})

defineEmits(['sort'])

function isSorted(column, direction) {
  return props.sortKey === column.key && props.sortDirection === direction
}

// ARIA：aria-sort 只标注当前生效的排序列，其余可排序列不输出该属性。
function ariaSort(column) {
  if (!column.sortable || props.sortKey !== column.key) return undefined
  return props.sortDirection === 'desc' ? 'descending' : 'ascending'
}

function rowKeyOf(row, index) {
  if (typeof props.rowKey === 'function') return props.rowKey(row, index)
  const value = row?.[props.rowKey]
  return value == null || value === '' ? index : String(value)
}

function cellText(row, column) {
  const value = row?.[column.key]
  return value == null ? '' : String(value)
}
</script>

<style scoped>
.ui-table {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.ui-table__table {
  width: 100%;
  border-collapse: collapse;
  color: var(--text-1);
  font-size: var(--text-sm);
}

.ui-table__table th {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--border);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0;
  text-align: left;
  white-space: nowrap;
}

.ui-table__table td {
  padding: var(--space-2) var(--space-3);
  vertical-align: middle;
  overflow-wrap: anywhere;
}

.ui-table__table tbody tr + tr td {
  border-top: 1px solid var(--border);
}

.ui-table__table .ui-table__cell--right {
  text-align: right;
}

.ui-table__sort {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: inherit;
  font: inherit;
  letter-spacing: 0;
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-standard);
}

.ui-table__sort:hover {
  color: var(--text-1);
}

.ui-table__sort:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.ui-table__sort-hint {
  color: var(--text-3);
}
</style>
