<!-- FeedToolbar：帖子列表工具栏（排序 + 筛选 chips + 清空），由 URL query 作为 SSOT。 -->
<template>
  <div class="feed-toolbar">
    <div class="feed-toolbar-left">
      <UiChips
        aria-label="排序"
        :wrap="false"
        :options="orderOptions"
        :model-value="order"
        @update:model-value="$emit('update:order', $event)"
      />

      <UiChips
        aria-label="筛选"
        :options="filterOptions"
        :model-value="filter"
        @update:model-value="$emit('update:filter', $event)"
      />

      <UiCheckbox
        v-if="showSubscribedToggle"
        class="subscribed-toggle"
        name="posts-subscribed-only"
        :disabled="disabled"
        :model-value="!!subscribed"
        label="仅看订阅"
        @update:modelValue="$emit('update:subscribed', $event)"
      />

      <div class="taxonomy-controls" v-if="categories.length > 0">
        <UiSelect
          id="posts-category-filter"
          name="posts-category-filter"
          class="taxonomy-select"
          :disabled="disabled"
          :model-value="String(categoryId || '')"
          aria-label="分类"
          :options="categoryOptions"
          placeholder="全部分类"
          @update:modelValue="$emit('update:categoryId', $event || '')"
        />
      </div>

      <div class="taxonomy-controls">
        <UiAutosuggestInput
          v-model.trim="tagDraft"
          id="posts-tag-filter"
          name="posts-tag-filter"
          placeholder="标签（回车确认）"
          autocomplete="off"
          :disabled="disabled"
          :suggestions="tagSuggestions"
          :commit-on-enter="true"
          :commit-on-blur="true"
          class="taxonomy-tag-input"
          @commit="$emit('update:tag', $event)"
        />
      </div>
    </div>

    <div class="feed-toolbar-right">
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
import { computed, ref, watch } from 'vue'
import UiAutosuggestInput from '../ui/UiAutosuggestInput.vue'
import UiButton from '../ui/UiButton.vue'
import UiCheckbox from '../ui/UiCheckbox.vue'
import UiChips from '../ui/UiChips.vue'
import UiSelect from '../ui/UiSelect.vue'

const props = defineProps({
  order: { type: String, default: 'latest' },
  filter: { type: String, default: '' },
  subscribed: { type: Boolean, default: false },
  showSubscribedToggle: { type: Boolean, default: false },
  categoryId: { type: [String, Number], default: '' },
  tag: { type: String, default: '' },
  categories: { type: Array, default: () => [] },
  tagSuggestions: { type: Array, default: () => [] },
  orderOptions: { type: Array, default: () => [] },
  filterOptions: { type: Array, default: () => [] },
  showClear: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:order', 'update:filter', 'update:subscribed', 'update:categoryId', 'update:tag', 'refresh', 'clear'])

const tagDraft = ref(String(props.tag || ''))
const categoryOptions = computed(() => [
  { label: '全部分类', value: '' },
  ...(Array.isArray(props.categories) ? props.categories : []).map((category) => ({
    label: category.name,
    value: String(category.id)
  }))
])

watch(
  () => props.tag,
  (v) => {
    tagDraft.value = String(v || '')
  }
)

</script>

<style scoped>
.feed-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
  border: 1px solid color-mix(in srgb, var(--border) 74%, transparent 26%);
  border-radius: 18px;
  box-shadow: none;
}

.feed-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.taxonomy-controls {
  display: inline-flex;
  align-items: center;
}

.subscribed-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  height: 34px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--border) 80%, transparent 20%);
  background: color-mix(in srgb, var(--surface) 88%, var(--bg) 12%);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 800;
  user-select: none;
}

.subscribed-toggle :deep(.ui-checkbox-input) {
  accent-color: var(--accent);
}

.subscribed-toggle:hover {
  background: color-mix(in srgb, var(--surface-2) 78%, transparent);
  border-color: var(--border-strong);
  color: var(--text-1);
}

.subscribed-toggle.disabled {
  opacity: 0.6;
}

.taxonomy-select {
  width: auto;
  min-width: 160px;
}

.taxonomy-select :deep(.ui-select-trigger) {
  height: 32px;
  font-size: 13px;
}

.taxonomy-tag-input {
  height: 32px;
  font-size: 13px;
  width: 200px;
}

.feed-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.feed-toolbar-button {
  height: 30px;
  min-width: 64px;
}

@media (max-width: 768px) {
  .feed-toolbar {
    align-items: flex-start;
    flex-direction: column;
    padding: 10px;
  }

  .feed-toolbar-right {
    width: 100%;
    justify-content: flex-start;
    margin-left: 0;
  }
}
</style>
