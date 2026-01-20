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

      <div class="taxonomy-controls" v-if="categories.length > 0">
        <select
          class="input taxonomy-select"
          :disabled="disabled"
          :value="String(categoryId || '')"
          aria-label="分类"
          @change="$emit('update:categoryId', $event?.target?.value || '')"
        >
          <option value="">全部分类</option>
          <option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
        </select>
      </div>

      <div class="taxonomy-controls">
        <UiInput
          v-model.trim="tagDraft"
          placeholder="标签（回车确认）"
          autocomplete="off"
          :disabled="disabled"
          :list="tagSuggestions.length > 0 ? tagDatalistId : null"
          class="taxonomy-tag-input"
          @keydown.enter.prevent="commitTag"
          @blur="commitTag"
        />
        <datalist v-if="tagSuggestions.length > 0" :id="tagDatalistId">
          <option v-for="t in tagSuggestions" :key="t" :value="t"></option>
        </datalist>
      </div>
    </div>

    <div class="feed-toolbar-right">
      <UiButton
        v-if="showClear"
        variant="secondary"
        style="height: 32px"
        :disabled="disabled"
        title="清空筛选与排序"
        @click="$emit('clear')"
      >
        清空
      </UiButton>

      <UiButton variant="ghost" style="height: 32px" :disabled="disabled" @click="$emit('refresh')">
        刷新
      </UiButton>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiChips from '../ui/UiChips.vue'
import UiInput from '../ui/UiInput.vue'

const props = defineProps({
  order: { type: String, default: 'latest' },
  filter: { type: String, default: '' },
  categoryId: { type: [String, Number], default: '' },
  tag: { type: String, default: '' },
  categories: { type: Array, default: () => [] },
  tagSuggestions: { type: Array, default: () => [] },
  orderOptions: { type: Array, default: () => [] },
  filterOptions: { type: Array, default: () => [] },
  showClear: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:order', 'update:filter', 'update:categoryId', 'update:tag', 'refresh', 'clear'])

const tagDraft = ref(String(props.tag || ''))
const tagDatalistId = 'posts-tag-suggest'

watch(
  () => props.tag,
  (v) => {
    tagDraft.value = String(v || '')
  }
)

function commitTag() {
  const next = String(tagDraft.value || '').trim()
  emit('update:tag', next)
}
</script>

<style scoped>
.feed-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 10px 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.feed-toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.taxonomy-controls {
  display: inline-flex;
  align-items: center;
}

.taxonomy-select {
  height: 32px;
  font-size: 13px;
  min-width: 140px;
}

.taxonomy-tag-input {
  height: 32px;
  font-size: 13px;
  width: 180px;
}

.feed-toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

@media (max-width: 768px) {
  .feed-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .feed-toolbar-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
