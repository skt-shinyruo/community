<template>
  <div class="page drive-page">
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
        <template v-if="workspace.mode === 'files'">
          <UiButton variant="secondary" :disabled="page.isBusy" @click="entries.toggleFolderComposer">
            {{ entries.creatingFolder ? '收起新建' : '新建文件夹' }}
          </UiButton>
          <UiButton :disabled="page.isBusy" @click="openFilePicker">
            {{ page.busyAction === 'upload' && upload.progress != null ? `上传 ${upload.progress}%` : '上传' }}
          </UiButton>
        </template>
        <UiButton v-if="page.busyAction === 'upload'" variant="secondary" @click="upload.cancel">取消上传</UiButton>
      </template>
    </UiPageHeader>

    <input
      ref="fileInputRef"
      class="sr-only"
      type="file"
      multiple
      tabindex="-1"
      aria-hidden="true"
      @change="upload.handleSelection"
    />

    <section class="drive-stats" aria-label="用量与当前位置">
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

    <div class="drive-layout">
      <UiCard class="drive-panel drive-main-panel">
        <UiTabs
          :model-value="workspace.mode"
          :tabs="modeTabs"
          label="网盘模式"
          @update:modelValue="workspace.switchMode"
        >
          <template #panel="{ tab, active }">
            <template v-if="active">
              <div v-if="tab.value === 'shares'" class="drive-tab-body">
                <div v-if="page.error" class="error drive-inline-error" role="alert">{{ page.error }}</div>

                <p class="drive-share-note">
                  <span v-if="workspace.selectedEntry">
                    {{ workspace.selectedEntry.canShare ? `为「${workspace.selectedEntry.name}」生成链接，或管理已有分享。` : '当前选择不可分享。' }}
                  </span>
                  <span v-else>先在「我的文件」中选中文件或文件夹，再生成分享链接。</span>
                </p>

                <div class="drive-share-form">
                  <UiField label="提取码">
                    <UiInput v-model.trim="shares.password" type="password" autocomplete="off" />
                  </UiField>
                  <UiField label="有效期">
                    <UiInput v-model="shares.expiresAt" type="datetime-local" />
                  </UiField>
                  <div v-if="shares.error" class="error drive-share-error" role="alert">{{ shares.error }}</div>
                  <div class="drive-share-form-actions">
                    <UiButton
                      :disabled="page.isBusy || !workspace.selectedEntry || !workspace.selectedEntry.canShare"
                      @click="shares.create"
                    >
                      生成分享链接
                    </UiButton>
                  </div>
                </div>

                <div v-if="shares.items.length > 0" class="drive-share-list">
                  <article v-for="item in shares.items" :key="item.shareId" class="drive-share-item">
                    <div class="drive-share-item-main">
                      <strong>{{ item.entryName }}</strong>
                      <span>{{ shares.statusLabel(item.status) }} · {{ item.expiresAt }}</span>
                      <code class="drive-share-link">{{ item.shareUrl }}</code>
                    </div>
                    <div class="drive-share-item-actions">
                      <UiButton v-if="item.status === 'ACTIVE'" variant="secondary" :disabled="page.isBusy" @click="shares.copy(item)">
                        复制链接
                      </UiButton>
                      <UiButton v-if="item.status === 'ACTIVE'" variant="dangerSecondary" :disabled="page.isBusy" @click="shares.revoke(item)">
                        撤销
                      </UiButton>
                    </div>
                  </article>
                </div>
                <UiState v-else>
                  暂无分享记录
                  <template #description>默认私有；生成链接后可用于帖子附件、成员分享或虚拟商品交付。</template>
                  <template #actions>
                    <UiButton variant="secondary" :disabled="page.isBusy" @click="workspace.switchMode('files')">
                      去选择文件
                    </UiButton>
                  </template>
                </UiState>
                <div v-if="shares.hasNext" class="drive-share-more">
                  <UiButton variant="secondary" :disabled="page.isBusy" @click="shares.loadMore">加载更多</UiButton>
                </div>
              </div>

              <div v-else class="drive-tab-body">
                <template v-if="tab.value === 'files'">
                  <div class="drive-search">
                    <div class="drive-search-field">
                      <UiInput
                        v-model.trim="workspace.searchKeyword"
                        type="search"
                        placeholder="搜索文件"
                        autocomplete="off"
                        aria-label="搜索文件"
                        @keydown.enter.prevent="workspace.search"
                      />
                    </div>
                    <UiButton variant="secondary" :disabled="page.isBusy" @click="workspace.search">搜索</UiButton>
                    <UiButton v-if="workspace.searchKeyword" variant="ghost" :disabled="page.isBusy" @click="workspace.clearSearch">
                      清除
                    </UiButton>
                  </div>

                  <div v-if="entries.creatingFolder" class="drive-inline-form">
                    <UiField label="文件夹名称" :error="entries.folderError">
                      <UiInput
                        v-model.trim="entries.folderNameDraft"
                        placeholder="输入文件夹名称"
                        autocomplete="off"
                        @keydown.enter.prevent="entries.createFolder"
                      />
                    </UiField>
                    <div class="drive-inline-actions">
                      <UiButton :disabled="page.isBusy" @click="entries.createFolder">确认</UiButton>
                      <UiButton variant="ghost" :disabled="page.isBusy" @click="entries.cancelCreateFolder">取消</UiButton>
                    </div>
                  </div>

                  <nav class="drive-path" aria-label="文件夹路径">
                    <UiBreadcrumb
                      :items="breadcrumbNavItems"
                      :disabled="page.isBusy"
                      @select="workspace.goBreadcrumb"
                    />
                  </nav>

                  <div
                    v-if="page.busyAction === 'upload'"
                    class="drive-upload-progress"
                    role="progressbar"
                    aria-label="上传进度"
                    aria-valuemin="0"
                    aria-valuemax="100"
                    :aria-valuenow="upload.progress ?? 0"
                  >
                    <div class="drive-upload-progress-fill" :style="{ width: `${upload.progress ?? 0}%` }"></div>
                  </div>
                </template>

                <DriveEntryList
                  :entries="workspace.visibleEntries"
                  :mode="tab.value"
                  :loading="page.loading"
                  :error="page.error"
                  :selected-entry-id="workspace.selectedEntryId"
                  :is-busy="page.isBusy"
                  @select="workspace.select"
                  @enter="workspace.enterFolder"
                  @restore="entries.restore"
                  @retry="page.reload"
                  @create-folder="entries.toggleFolderComposer"
                  @upload="openFilePicker"
                  @back-to-files="workspace.switchMode('files')"
                />
              </div>
            </template>
          </template>
        </UiTabs>
      </UiCard>

      <UiCard class="drive-panel drive-detail-panel">
        <template v-if="workspace.selectedEntry">
          <div class="drive-detail-head">
            <h2 class="drive-detail-title">{{ workspace.selectedEntry.name }}</h2>
            <p class="drive-detail-subtitle">
              {{ workspace.selectedEntry.isFolder ? '文件夹' : formatDriveBytes(workspace.selectedEntry.sizeBytes) }}
              <span class="drive-header-dot" aria-hidden="true">·</span>
              {{ workspace.selectedEntry.statusLabel }}
            </p>
          </div>

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
            <UiField label="重命名" :error="entries.renameError">
              <UiInput v-model.trim="workspace.renameDraft" placeholder="输入新名称" autocomplete="off" />
            </UiField>
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
      </UiCard>
    </div>

    <UiModalConfirm
      v-if="confirmation.open"
      :title="confirmation.title"
      :message="confirmation.message"
      :confirm-text="confirmation.confirmText"
      :confirm-variant="confirmation.variant"
      @cancel="closeConfirmation"
      @confirm="runConfirmation"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiState from '../components/ui/UiState.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import DriveEntryList from './drive/DriveEntryList.vue'
import { formatDriveBytes } from './driveState'
import { useDrivePageState } from './drive/useDrivePageState'

const { page, workspace, entries, upload, shares, confirmation, closeConfirmation, runConfirmation } = useDrivePageState()

const modeTabs = [
  { value: 'files', label: '我的文件' },
  { value: 'shares', label: '分享管理' },
  { value: 'trash', label: '回收站' }
]

const breadcrumbNavItems = computed(() =>
  workspace.breadcrumbItems.map((item) => ({ label: item.name }))
)

const fileInputRef = ref(null)

function openFilePicker() {
  if (page.isBusy) return
  fileInputRef.value?.click()
}
</script>

<style scoped>
.drive-header-dot {
  opacity: 0.6;
}

.drive-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.drive-stat {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-width: 0;
}

.drive-stat span {
  font-size: 13px;
  color: var(--text-3);
}

.drive-stat strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.9fr);
  gap: var(--space-4);
  align-items: start;
}

.drive-panel {
  min-width: 0;
}

.drive-tab-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.drive-search {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.drive-search-field {
  flex: 1;
  min-width: min(100%, 240px);
}

.drive-inline-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: end;
}

.drive-inline-actions,
.drive-action-row,
.drive-share-item-actions,
.drive-share-form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.drive-path .breadcrumb {
  margin-bottom: 0;
}

.drive-upload-progress {
  height: 6px;
  border-radius: var(--radius-full);
  background: var(--surface-2);
  overflow: hidden;
}

.drive-upload-progress-fill {
  height: 100%;
  min-width: 2px;
  background: var(--accent);
  border-radius: var(--radius-full);
  transition: width var(--duration-base) var(--ease-standard);
}

.drive-inline-error,
.drive-share-error {
  font-size: var(--text-sm);
}

.drive-share-note {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
}

.drive-share-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.drive-share-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.drive-share-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
}

.drive-share-item-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.drive-share-item-main > span {
  color: var(--text-3);
  font-size: 13px;
}

.drive-share-link {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-2);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}

.drive-share-more {
  display: flex;
  justify-content: center;
}

.drive-detail-head {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

.drive-detail-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 650;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-detail-subtitle {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
}

.drive-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.drive-detail-grid div {
  min-width: 0;
}

.drive-detail-grid dt {
  font-size: 13px;
  color: var(--text-3);
}

.drive-detail-grid dd {
  margin: var(--space-1) 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drive-action-stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.drive-detail-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
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

  .drive-inline-form {
    grid-template-columns: 1fr;
  }
}
</style>
