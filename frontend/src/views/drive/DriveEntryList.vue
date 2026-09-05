<!-- 网盘条目列表：首载骨架、可重试错态、带主要下一步的空态和文件夹/文件行；
     我的文件与回收站两个 tab 共用，行操作差异由 mode 驱动。 -->
<template>
  <div class="drive-entry-section">
    <UiSkeleton v-if="loading && entries.length === 0" variant="list" :rows="4" label="加载网盘" />

    <UiState v-else-if="error && entries.length === 0" variant="error" :title="error">
      <template #description>网盘数据加载失败，可以重试或稍后再来。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="isBusy" @click="$emit('retry')">重试</UiButton>
      </template>
    </UiState>

    <template v-else>
      <div v-if="error" class="error drive-entry-error" role="alert">{{ error }}</div>

      <UiState v-if="!loading && entries.length === 0">
        {{ mode === 'trash' ? '回收站是空的' : '暂无文件' }}
        <template #description>
          {{ mode === 'trash' ? '删除到回收站的条目会出现在这里，可以恢复或彻底删除。' : '可以先创建文件夹，或者上传一个文件。' }}
        </template>
        <template #actions>
          <UiButton v-if="mode === 'trash'" variant="secondary" :disabled="isBusy" @click="$emit('backToFiles')">
            返回我的文件
          </UiButton>
          <template v-else>
            <UiButton variant="secondary" :disabled="isBusy" @click="$emit('createFolder')">新建文件夹</UiButton>
            <UiButton :disabled="isBusy" @click="$emit('upload')">上传文件</UiButton>
          </template>
        </template>
      </UiState>

      <div v-else class="drive-entry-list">
        <div
          v-for="entry in entries"
          :key="entry.entryId"
          class="drive-entry-row"
          :class="{ selected: entry.entryId === selectedEntryId }"
          role="button"
          tabindex="0"
          @click="$emit('select', entry)"
          @keydown.enter.prevent="$emit('select', entry)"
          @keydown.space.prevent="$emit('select', entry)"
        >
          <span class="drive-entry-icon" aria-hidden="true">
            <component :is="entry.isFolder ? Folder : File" :size="18" />
          </span>
          <div class="drive-entry-main">
            <strong class="drive-entry-name">{{ entry.name }}</strong>
            <span class="drive-entry-subtitle">
              {{ entry.isFolder ? '文件夹' : formatDriveBytes(entry.sizeBytes) }}
            </span>
          </div>
          <div class="drive-entry-meta">
            <span>{{ entry.statusLabel }}</span>
            <span>{{ entry.visibilityLabel }}</span>
          </div>
          <div class="drive-entry-actions">
            <UiButton
              v-if="mode === 'files' && entry.isFolder"
              variant="ghost"
              :disabled="isBusy"
              @click.stop="$emit('enter', entry)"
            >
              进入
            </UiButton>
            <UiButton
              v-else-if="mode === 'trash'"
              variant="secondary"
              :disabled="isBusy"
              @click.stop="$emit('restore', entry)"
            >
              恢复
            </UiButton>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { File, Folder } from 'lucide-vue-next'
import UiButton from '../../components/ui/UiButton.vue'
import UiSkeleton from '../../components/ui/UiSkeleton.vue'
import UiState from '../../components/ui/UiState.vue'
import { formatDriveBytes } from '../driveState'

defineProps({
  entries: { type: Array, default: () => [] },
  mode: { type: String, default: 'files' }, // files | trash
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  selectedEntryId: { type: String, default: '' },
  isBusy: { type: Boolean, default: false }
})

defineEmits(['select', 'enter', 'restore', 'retry', 'createFolder', 'upload', 'backToFiles'])
</script>

<style scoped>
.drive-entry-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.drive-entry-error {
  font-size: var(--text-sm);
}

.drive-entry-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.drive-entry-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.drive-entry-row:hover {
  border-color: var(--border-strong);
}

.drive-entry-row.selected {
  background: var(--accent-weak);
  border-color: var(--accent);
}

.drive-entry-row:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.drive-entry-icon {
  display: flex;
  align-items: center;
  color: var(--text-3);
}

.drive-entry-row.selected .drive-entry-icon {
  color: var(--accent-text);
}

.drive-entry-main,
.drive-entry-meta,
.drive-entry-actions {
  min-width: 0;
}

.drive-entry-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.drive-entry-name,
.drive-entry-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-entry-subtitle,
.drive-entry-meta {
  color: var(--text-3);
  font-size: 13px;
}

.drive-entry-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-1);
}

.drive-entry-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (max-width: 720px) {
  .drive-entry-row {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .drive-entry-meta,
  .drive-entry-actions {
    grid-column: 1 / -1;
  }

  .drive-entry-meta {
    flex-direction: row;
    align-items: flex-start;
    gap: var(--space-3);
  }
}
</style>
