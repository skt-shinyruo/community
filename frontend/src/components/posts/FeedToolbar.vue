<!-- FeedToolbar：公共信息流工具栏（版块切换 + 刷新）。 -->
<template>
  <div class="feed-toolbar">
    <div class="feed-toolbar-left">
      <div class="taxonomy-controls" v-if="categories.length > 0">
        <UiSelect
          id="posts-board-filter"
          name="posts-board-filter"
          class="taxonomy-select"
          :disabled="disabled"
          :model-value="String(boardId || '')"
          aria-label="版块"
          :options="boardOptions"
          placeholder="全部版块"
          @update:modelValue="$emit('update:boardId', $event || '')"
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
import { computed } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiSelect from '../ui/UiSelect.vue'

const props = defineProps({
  boardId: { type: [String, Number], default: '' },
  categories: { type: Array, default: () => [] },
  showClear: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

defineEmits(['update:boardId', 'refresh', 'clear'])

const boardOptions = computed(() => [
  { label: '全部版块', value: '' },
  ...(Array.isArray(props.categories) ? props.categories : []).map((category) => ({
    label: category.name,
    value: String(category.id)
  }))
])
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

.taxonomy-select {
  width: auto;
  min-width: 160px;
}

.taxonomy-select :deep(.ui-select-trigger) {
  height: 32px;
  font-size: 13px;
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
