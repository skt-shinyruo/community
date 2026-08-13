<template>
  <div class="page drive-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>网盘</template>
      <template #subtitle>
        <span>{{ entries.quota.label }}</span>
        <span class="drive-header-dot" aria-hidden="true">·</span>
        <span>{{ entries.quota.usedPercent }}% 已用</span>
        <span class="drive-header-dot" aria-hidden="true">·</span>
        <span>私有文件、分享链接和社区附件</span>
      </template>
      <template #actions>
        <UiButton variant="secondary" :disabled="page.isBusy" @click="page.reload">
          {{ page.loading ? '刷新中…' : '刷新' }}
        </UiButton>
        <UiButton v-if="workspace.mode !== 'trash'" variant="secondary" :disabled="page.isBusy" @click="entries.toggleFolderComposer">
          {{ entries.creatingFolder ? '收起新建' : '新建文件夹' }}
        </UiButton>
        <label v-if="workspace.mode !== 'trash'" class="btn drive-upload-label" :class="{ 'is-disabled': page.isBusy }">
          {{ page.busyAction === 'upload' && upload.progress != null ? `上传 ${upload.progress}%` : '上传' }}
          <input class="sr-only" type="file" multiple :disabled="page.isBusy" @change="upload.handleSelection">
        </label>
        <UiButton v-if="page.busyAction === 'upload'" variant="secondary" @click="upload.cancel">取消上传</UiButton>
      </template>
    </UiPageHeader>

    <section class="drive-stats">
      <div class="drive-stat">
        <span>已用空间</span>
        <strong>{{ formatDriveBytes(entries.quota.usedBytes) }}</strong>
      </div>
      <div class="drive-stat">
        <span>剩余空间</span>
        <strong>{{ formatDriveBytes(entries.quota.remainingBytes) }}</strong>
      </div>
      <div class="drive-stat">
        <span>当前目录</span>
        <strong>{{ workspace.currentFolderLabel }}</strong>
      </div>
      <div class="drive-stat">
        <span>当前条目</span>
        <strong>{{ workspace.visibleEntries.length }}</strong>
      </div>
    </section>

    <div v-if="page.error" class="drive-banner drive-banner--error">{{ page.error }}</div>
    <div v-else-if="page.statusMessage" class="drive-banner">{{ page.statusMessage }}</div>

    <div class="drive-layout">
      <UiCard class="drive-panel drive-main-panel">
        <div class="drive-toolbar">
          <div class="drive-tabs" role="tablist" aria-label="网盘模式">
            <button type="button" class="drive-tab" :class="{ active: workspace.mode === 'files' }" :disabled="page.isBusy" @click="workspace.switchMode('files')">
              我的文件
            </button>
            <button type="button" class="drive-tab" :class="{ active: workspace.mode === 'shares' }" :disabled="page.isBusy" @click="workspace.switchMode('shares')">
              分享管理
            </button>
            <button type="button" class="drive-tab" :class="{ active: workspace.mode === 'trash' }" :disabled="page.isBusy" @click="workspace.switchMode('trash')">
              回收站
            </button>
          </div>

          <div class="drive-search">
            <UiInput
              v-model.trim="workspace.searchKeyword"
              type="search"
              placeholder="搜索文件"
              autocomplete="off"
              @keydown.enter.prevent="workspace.search"
            />
            <UiButton variant="secondary" :disabled="page.isBusy" @click="workspace.search">搜索</UiButton>
            <UiButton v-if="workspace.searchKeyword" variant="ghost" :disabled="page.isBusy" @click="workspace.clearSearch">清除</UiButton>
          </div>
        </div>

        <div v-if="entries.creatingFolder && workspace.mode !== 'trash'" class="drive-inline-form">
          <label class="drive-field">
            <span>文件夹名称</span>
            <UiInput v-model.trim="entries.folderNameDraft" placeholder="输入文件夹名称" autocomplete="off" />
          </label>
          <div class="drive-inline-actions">
            <UiButton :disabled="page.isBusy" @click="entries.createFolder">确认</UiButton>
            <UiButton variant="ghost" :disabled="page.isBusy" @click="entries.cancelCreateFolder">取消</UiButton>
          </div>
        </div>

        <div class="drive-breadcrumb" aria-label="文件夹路径">
          <button
            v-for="(item, index) in workspace.breadcrumbItems"
            :key="item.entryId || 'root'"
            type="button"
            class="drive-breadcrumb-item"
            :class="{ active: index === workspace.breadcrumbItems.length - 1 }"
            :disabled="page.isBusy"
            @click="workspace.goBreadcrumb(index)"
          >
            {{ item.name }}
          </button>
        </div>

        <UiState v-if="page.loading && workspace.visibleEntries.length === 0">
          正在加载网盘…
        </UiState>
        <UiState v-else-if="!page.loading && workspace.visibleEntries.length === 0">
          暂无文件
          <template #description>
            {{ workspace.mode === 'trash' ? '回收站目前是空的。' : '可以先创建文件夹，或者上传一个文件。' }}
          </template>
        </UiState>

        <div v-else class="drive-entry-list">
          <div
            v-for="entry in workspace.visibleEntries"
            :key="entry.entryId"
            class="drive-entry-row"
            :class="{ selected: entry.entryId === workspace.selectedEntryId }"
            role="button"
            tabindex="0"
            @click="workspace.select(entry)"
            @keydown.enter.prevent="workspace.select(entry)"
          >
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
                v-if="workspace.mode === 'files' && entry.isFolder"
                variant="ghost"
                :disabled="page.isBusy"
                @click.stop="workspace.enterFolder(entry)"
              >
                进入
              </UiButton>
              <UiButton
                v-else-if="workspace.mode === 'trash'"
                variant="secondary"
                @click.stop="entries.restore(entry)"
              >
                恢复
              </UiButton>
            </div>
          </div>
        </div>
      </UiCard>

      <UiCard class="drive-panel drive-detail-panel">
        <template v-if="workspace.selectedEntry">
          <UiPageHeader>
            <template #title>{{ workspace.selectedEntry.name }}</template>
            <template #subtitle>
              <span>{{ workspace.selectedEntry.isFolder ? '文件夹' : formatDriveBytes(workspace.selectedEntry.sizeBytes) }}</span>
              <span class="drive-header-dot" aria-hidden="true">·</span>
              <span>{{ workspace.selectedEntry.statusLabel }}</span>
            </template>
          </UiPageHeader>

          <dl class="drive-detail-grid">
            <div>
              <dt>类型</dt>
              <dd>{{ workspace.selectedEntry.isFolder ? '文件夹' : '文件' }}</dd>
            </div>
            <div>
              <dt>位置</dt>
              <dd>{{ workspace.currentFolderLabel }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ workspace.selectedEntry.statusLabel }}</dd>
            </div>
            <div>
              <dt>可见性</dt>
              <dd>{{ workspace.selectedEntry.visibilityLabel }}</dd>
            </div>
          </dl>

          <div v-if="workspace.mode !== 'trash'" class="drive-action-stack">
            <label class="drive-field">
              <span>重命名</span>
              <UiInput v-model.trim="workspace.renameDraft" placeholder="输入新名称" autocomplete="off" />
            </label>
            <div class="drive-action-row">
              <UiButton :disabled="page.isBusy || !workspace.renameDraft.trim()" @click="entries.renameSelected">重命名</UiButton>
              <UiButton variant="secondary" :disabled="page.isBusy" @click="entries.moveSelectedHere">移动到当前目录</UiButton>
            </div>
            <div class="drive-action-row">
              <UiButton v-if="workspace.selectedEntry.canDownload" variant="secondary" :disabled="page.isBusy" @click="entries.downloadSelected">
                下载
              </UiButton>
              <UiButton v-if="workspace.selectedEntry.canShare" variant="secondary" :disabled="page.isBusy" @click="shares.open">
                分享
              </UiButton>
              <UiButton v-if="workspace.selectedEntry.canTrash" variant="danger" :disabled="page.isBusy" @click="entries.trashSelected">
                删除
              </UiButton>
            </div>
          </div>

          <div v-else class="drive-action-stack">
            <div class="drive-action-row">
              <UiButton variant="secondary" :disabled="page.isBusy" @click="entries.restoreSelected">恢复到当前目录</UiButton>
              <UiButton variant="danger" :disabled="page.isBusy" @click="entries.deleteSelectedPermanently">彻底删除</UiButton>
            </div>
          </div>
        </template>

        <UiState v-else>
          选择一个文件或文件夹查看详情
        </UiState>

        <section v-if="workspace.mode === 'shares'" class="drive-share-panel">
          <UiPageHeader>
            <template #title>分享管理</template>
            <template #subtitle>默认私有；生成链接后可用于帖子附件、成员分享或虚拟商品交付。</template>
          </UiPageHeader>

          <div class="drive-share-note">
            <span v-if="workspace.selectedEntry">{{ workspace.selectedEntry.canShare ? workspace.selectedEntry.name : '当前选择不可分享' }}</span>
            <span v-else>先选中文件或文件夹，再生成分享链接。</span>
          </div>

          <div class="drive-share-form">
            <label class="drive-field">
              <span>提取码</span>
              <UiInput v-model.trim="shares.password" type="password" autocomplete="off" />
            </label>
            <label class="drive-field">
              <span>有效期</span>
              <input v-model="shares.expiresAt" class="input" type="datetime-local">
            </label>
            <UiButton :disabled="page.isBusy || !workspace.selectedEntry || !workspace.selectedEntry.canShare" @click="shares.create">
              生成分享链接
            </UiButton>
            <div v-if="shares.error" class="error">{{ shares.error }}</div>
          </div>

          <div v-if="shares.items.length > 0" class="drive-share-list">
            <article v-for="item in shares.items" :key="item.shareId" class="drive-share-item">
              <div class="drive-share-item-main">
                <strong>{{ item.entryName }}</strong>
                <span>{{ shares.statusLabel(item.status) }} · {{ item.expiresAt }}</span>
                <code class="drive-share-link">{{ item.shareUrl }}</code>
              </div>
              <div class="drive-share-item-actions">
                <UiButton v-if="item.status === 'ACTIVE'" variant="secondary" :disabled="page.isBusy" @click="shares.copy(item)">复制链接</UiButton>
                <UiButton v-if="item.status === 'ACTIVE'" variant="dangerSecondary" :disabled="page.isBusy" @click="shares.revoke(item)">撤销</UiButton>
              </div>
            </article>
          </div>
          <UiState v-else>暂无分享记录</UiState>
          <UiButton v-if="shares.hasNext" variant="secondary" :disabled="page.isBusy" @click="shares.loadMore">
            加载更多
          </UiButton>
        </section>
      </UiCard>
    </div>
  </div>
</template>

<script setup>
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { formatDriveBytes } from './driveState'
import { useDrivePageState } from './drive/useDrivePageState'

const { page, workspace, entries, upload, shares } = useDrivePageState()
</script>

<style scoped>
.drive-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drive-header-dot {
  opacity: 0.6;
}

.drive-upload-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.drive-upload-label.is-disabled {
  pointer-events: none;
  opacity: 0.6;
}

.drive-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.drive-stat {
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  border-radius: 8px;
  padding: 12px 14px;
  background: var(--panel-bg, rgba(255, 255, 255, 0.6));
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.drive-stat span {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-stat strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-banner {
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  border-radius: 8px;
  padding: 10px 12px;
  background: rgba(59, 130, 246, 0.08);
}

.drive-banner--error {
  background: rgba(239, 68, 68, 0.08);
}

.drive-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.drive-panel {
  min-width: 0;
}

.drive-main-panel,
.drive-detail-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drive-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.drive-tabs {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drive-tab {
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  background: transparent;
  color: inherit;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
}

.drive-tab.active {
  background: rgba(59, 130, 246, 0.12);
  border-color: rgba(59, 130, 246, 0.35);
}

.drive-search {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-width: min(100%, 440px);
}

.drive-inline-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.drive-inline-actions,
.drive-action-row,
.drive-share-item-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drive-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.drive-field span {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.drive-breadcrumb-item {
  border: 0;
  background: transparent;
  color: inherit;
  padding: 0;
  cursor: pointer;
}

.drive-breadcrumb-item.active {
  font-weight: 600;
}

.drive-entry-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.drive-entry-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
  cursor: pointer;
}

.drive-entry-row.selected {
  background: rgba(59, 130, 246, 0.08);
}

.drive-entry-main,
.drive-entry-meta,
.drive-entry-actions {
  min-width: 0;
}

.drive-entry-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drive-entry-name,
.drive-entry-subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-entry-subtitle,
.drive-entry-meta {
  color: var(--muted, #667085);
  font-size: 12px;
}

.drive-entry-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.drive-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.drive-detail-grid div {
  min-width: 0;
}

.drive-detail-grid dt {
  font-size: 12px;
  color: var(--muted, #667085);
}

.drive-detail-grid dd {
  margin: 4px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-action-stack,
.drive-share-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drive-share-note {
  color: var(--muted, #667085);
  font-size: 13px;
}

.drive-share-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drive-share-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.drive-share-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px solid var(--border-color, rgba(120, 130, 150, 0.22));
}

.drive-share-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drive-share-link {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 4px 6px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.05);
}

@media (max-width: 1100px) {
  .drive-layout {
    grid-template-columns: 1fr;
  }

  .drive-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .drive-stats {
    grid-template-columns: 1fr;
  }

  .drive-entry-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .drive-entry-meta {
    align-items: flex-start;
  }

  .drive-inline-form {
    grid-template-columns: 1fr;
  }
}
</style>
