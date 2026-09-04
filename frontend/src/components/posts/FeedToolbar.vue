<!-- FeedToolbar：帖子流筛选工具栏（分类入口 + 活动标签 chip + 清空/刷新）。
     最新/最热排序由 PostsView 的 UiTabs 承载；分类用 UiDropdown，活动标签以可清除 chip 呈现。 -->
<template>
  <div class="feed-toolbar">
    <div class="feed-toolbar-filters">
      <UiDropdown
        v-if="categories.length > 0"
        :items="categoryItems"
        :disabled="disabled"
        label="分类"
        @select="onCategorySelect"
      >
        <span class="feed-toolbar-category-current">{{ currentCategoryLabel }}</span>
        <ChevronDown :size="14" aria-hidden="true" />
      </UiDropdown>

      <span v-if="tag" class="feed-toolbar-tag-chip">
        <span class="feed-toolbar-tag-chip-text">#{{ tag }}</span>
        <button
          type="button"
          class="feed-toolbar-tag-chip-clear"
          :aria-label="`清除标签 ${tag}`"
          :disabled="disabled"
          @click="$emit('clearTag')"
        >
          <X :size="12" aria-hidden="true" />
        </button>
      </span>
    </div>

    <div class="feed-toolbar-actions">
      <UiButton
        v-if="showClear"
        variant="secondary"
        class="feed-toolbar-button"
        :disabled="disabled"
        title="清空筛选与排序"
        @click="$emit('clear')"
      >
        清空
      </UiButton>

      <UiButton variant="ghost" class="feed-toolbar-button" :disabled="disabled" @click="$emit('refresh')">
        刷新
      </UiButton>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ChevronDown, X } from 'lucide-vue-next'
import UiButton from '../ui/UiButton.vue'
import UiDropdown from '../ui/UiDropdown.vue'

const props = defineProps({
  categoryId: { type: [String, Number], default: '' },
  tag: { type: String, default: '' },
  categories: { type: Array, default: () => [] },
  showClear: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:categoryId', 'clearTag', 'refresh', 'clear'])

const categoryItems = computed(() => [
  { label: '全部分类', value: '' },
  ...(Array.isArray(props.categories) ? props.categories : []).map((category) => ({
    label: category.name,
    value: String(category.id)
  }))
])

const currentCategoryLabel = computed(() => {
  const current = categoryItems.value.find((item) => item.value === String(props.categoryId || ''))
  return current?.label || '全部分类'
})

function onCategorySelect(item) {
  emit('update:categoryId', item?.value || '')
}
</script>

<style scoped>
.feed-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.feed-toolbar-filters {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  min-width: 0;
}

.feed-toolbar-category-current {
  max-width: 12em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-toolbar-tag-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 26px;
  padding: 0 var(--space-1) 0 var(--space-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: var(--text-xs);
  font-weight: 600;
}

.feed-toolbar-tag-chip-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
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

.feed-toolbar-tag-chip-clear:hover:not(:disabled) {
  color: var(--text-1);
  background: var(--hover-bg);
}

.feed-toolbar-tag-chip-clear:focus-visible {
  box-shadow: var(--focus-ring);
}

.feed-toolbar-tag-chip-clear:disabled {
  color: var(--muted);
  cursor: not-allowed;
}

.feed-toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-left: auto;
}

@media (max-width: 768px) {
  .feed-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .feed-toolbar-actions {
    width: 100%;
    justify-content: flex-start;
    margin-left: 0;
  }
}
</style>
