<template>
  <div class="page moderation-page">
    <UiBreadcrumb />

    <UiCard class="moderation-shell">
      <UiPageHeader>
        <template #title>治理后台</template>
        <template #subtitle>聚焦待处理举报、处置审计和高风险动作的执行上下文。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
        </template>
      </UiPageHeader>

      <div class="moderation-tabs">
        <UiButton :variant="tab === 'reports' ? 'primary' : 'secondary'" @click="tab = 'reports'">举报队列</UiButton>
        <UiButton :variant="tab === 'actions' ? 'primary' : 'secondary'" @click="tab = 'actions'">处置审计</UiButton>
      </div>

      <UiEmpty v-if="error" type="error" class="moderation-state">{{ error }}</UiEmpty>
      <div v-else-if="loading" class="muted moderation-loading">加载中…</div>

      <div v-else class="moderation-body">
        <template v-if="tab === 'reports'">
          <div class="moderation-filter-bar">
            <div class="moderation-filter-label muted">状态</div>
            <UiSelect
              v-model="statusFilter"
              name="moderation-status-filter"
              class="moderation-filter-select"
              aria-label="举报状态"
              :options="statusFilterOptions"
            />
          </div>

          <UiEmpty v-if="reports.length === 0">暂无举报</UiEmpty>

          <div v-else class="moderation-list">
            <div v-for="r in reports" :key="r.id" class="card flat report-row moderation-report-card">
              <div class="moderation-report-layout">
                <div class="moderation-report-main">
                  <div class="moderation-report-tags">
                    <span class="tag">#{{ r.id }}</span>
                    <span class="tag">{{ targetTypeLabel(r.targetType) }} #{{ r.targetId }}</span>
                    <UiBadge v-if="Number(r.status) === 0" variant="warning" class="moderation-status-badge">待处理</UiBadge>
                    <UiBadge v-else-if="Number(r.status) === 1" variant="success" class="moderation-status-badge">已处理</UiBadge>
                    <UiBadge v-else variant="secondary" class="moderation-status-badge">已驳回</UiBadge>
                  </div>

                  <div class="moderation-report-title">{{ r.reason }}</div>
                  <div v-if="r.detail" class="muted moderation-report-detail">{{ r.detail }}</div>

                  <div class="muted moderation-report-meta">
                    举报人：{{ r.reporterId }} · {{ formatTime(r.createTime) }}
                  </div>
                </div>

                <div class="moderation-report-side">
                  <UiButton variant="secondary" :disabled="actionLoading" @click="openActionModal(r)">处置</UiButton>
                </div>
              </div>
            </div>

            <div class="moderation-pagination">
              <UiButton v-if="reportsHasNext" variant="secondary" :disabled="loadingMore" @click="loadMoreReports">
                {{ loadingMore ? '加载中…' : '加载更多' }}
              </UiButton>
            </div>
          </div>
        </template>

        <template v-else>
          <UiEmpty v-if="actions.length === 0">暂无处置记录</UiEmpty>

          <div v-else class="moderation-list">
            <div v-for="a in actions" :key="a.id" class="card flat moderation-action-card">
              <div class="moderation-action-head">
                <div class="moderation-report-tags">
                  <span class="tag">#{{ a.id }}</span>
                  <span class="tag">report #{{ a.reportId }}</span>
                  <span class="tag">{{ a.action }}</span>
                </div>
                <div class="muted moderation-report-meta">{{ formatTime(a.createTime) }}</div>
              </div>
              <div class="moderation-report-title">{{ a.reason }}</div>
              <div class="muted moderation-report-meta">操作者：{{ a.actorId }} · 时长：{{ Number(a.durationSeconds || 0) }}s</div>
            </div>

            <div class="moderation-pagination">
              <UiButton v-if="actionsHasNext" variant="secondary" :disabled="loadingMore" @click="loadMoreActions">
                {{ loadingMore ? '加载中…' : '加载更多' }}
              </UiButton>
            </div>
          </div>
        </template>
      </div>
    </UiCard>
  </div>

  <!-- Action Modal -->
  <div v-if="actionModalOpen" class="modal-mask" @click.self="closeActionModal">
    <div class="modal-card card moderation-modal">
      <div class="moderation-modal-body">
        <div class="moderation-modal-head">
          <div class="moderation-modal-title">处置举报 #{{ selectedReport?.id }}</div>
          <UiIconButton aria-label="关闭" title="关闭" size="sm" @click="closeActionModal">×</UiIconButton>
        </div>

        <div class="muted moderation-modal-meta">
          目标：{{ targetTypeLabel(selectedReport?.targetType) }} #{{ selectedReport?.targetId }}
        </div>

        <div class="moderation-modal-field">
          <div class="muted moderation-modal-label">动作</div>
          <UiSelect
            v-model="actionForm.action"
            name="moderation-action-type"
            class="moderation-modal-select"
            aria-label="处置动作"
            :disabled="actionLoading"
            :options="actionOptions"
          />
        </div>

        <div class="moderation-modal-field">
          <div class="muted moderation-modal-label">理由（必填）</div>
            <UiTextarea v-model.trim="actionForm.reason" name="moderation-action-reason" :rows="3" placeholder="简要说明处置原因" :disabled="actionLoading" />
        </div>

        <div v-if="actionNeedsDuration" class="moderation-modal-field">
          <div class="muted moderation-modal-label">时长</div>
          <div class="moderation-modal-duration">
            <UiSelect
              v-model="actionForm.durationPreset"
              name="moderation-duration-preset"
              class="moderation-modal-select moderation-modal-select--duration"
              aria-label="处置时长"
              :disabled="actionLoading"
              :options="durationPresetOptions"
            />

            <UiInput
              v-if="actionForm.durationPreset === 'custom'"
              v-model.trim="actionForm.durationSeconds"
              name="moderation-duration-seconds"
              class="moderation-modal-custom-duration"
              placeholder="秒，例如 600"
              :disabled="actionLoading"
            />
          </div>
        </div>

        <div v-if="actionError" class="error moderation-modal-error">{{ actionError }}</div>

        <div class="moderation-modal-actions">
          <UiButton variant="secondary" :disabled="actionLoading" @click="closeActionModal">取消</UiButton>
          <UiButton :disabled="actionLoading" @click="submitAction">{{ actionLoading ? '处理中…' : '确认处置' }}</UiButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiIconButton from '../components/ui/UiIconButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import { formatTime } from '../utils/time'
import { listActions, listReports, takeAction } from '../api/services/moderationService'

const tab = ref('reports')

const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')

// reports
const reports = ref([])
const reportsPage = ref(0)
const reportsSize = 20
const reportsHasNext = ref(true)
const statusFilter = ref('0')
const statusFilterOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: '0' },
  { label: '已处理', value: '1' },
  { label: '已驳回', value: '2' }
]

// actions
const actions = ref([])
const actionsPage = ref(0)
const actionsSize = 20
const actionsHasNext = ref(true)
const actionOptions = [
  { label: '驳回', value: 'reject' },
  { label: '隐藏', value: 'hide' },
  { label: '删除', value: 'delete' },
  { label: '警告', value: 'warn' },
  { label: '禁言', value: 'mute' },
  { label: '封禁', value: 'ban' }
]
const durationPresetOptions = [
  { label: '1 小时', value: '3600' },
  { label: '1 天', value: '86400' },
  { label: '7 天', value: '604800' },
  { label: '30 天', value: '2592000' },
  { label: '自定义', value: 'custom' }
]

function targetTypeLabel(t) {
  const n = Number(t || 0)
  if (n === 1) return '帖子'
  if (n === 2) return '评论'
  if (n === 3) return '用户'
  return '目标'
}

async function loadReports(append = false) {
  if (append) loadingMore.value = true
  else loading.value = true
  if (!append) error.value = ''

  try {
    const resp = await listReports({
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
      page: reportsPage.value,
      size: reportsSize
    })
    const list = Array.isArray(resp?.data) ? resp.data : []
    reportsHasNext.value = list.length >= reportsSize
    reports.value = append ? [...reports.value, ...list] : list
  } catch (e) {
    if (!append) error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function loadActions(append = false) {
  if (append) loadingMore.value = true
  else loading.value = true
  if (!append) error.value = ''

  try {
    const resp = await listActions({ page: actionsPage.value, size: actionsSize })
    const list = Array.isArray(resp?.data) ? resp.data : []
    actionsHasNext.value = list.length >= actionsSize
    actions.value = append ? [...actions.value, ...list] : list
  } catch (e) {
    if (!append) error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function reload() {
  if (tab.value === 'reports') {
    reportsPage.value = 0
    await loadReports(false)
  } else {
    actionsPage.value = 0
    await loadActions(false)
  }
}

async function loadMoreReports() {
  if (!reportsHasNext.value) return
  reportsPage.value += 1
  await loadReports(true)
}

async function loadMoreActions() {
  if (!actionsHasNext.value) return
  actionsPage.value += 1
  await loadActions(true)
}

watch(statusFilter, () => {
  if (tab.value !== 'reports') return
  reportsPage.value = 0
  loadReports(false)
})

watch(tab, () => {
  reload()
})

onMounted(reload)

// action modal
const actionModalOpen = ref(false)
const selectedReport = ref(null)
const actionLoading = ref(false)
const actionError = ref('')
const actionForm = ref({
  action: 'reject',
  reason: '',
  durationPreset: '86400',
  durationSeconds: ''
})

const actionNeedsDuration = computed(() => actionForm.value.action === 'mute' || actionForm.value.action === 'ban')

function openActionModal(report) {
  selectedReport.value = report || null
  actionForm.value = { action: 'reject', reason: '', durationPreset: '86400', durationSeconds: '' }
  actionError.value = ''
  actionModalOpen.value = true
}

function closeActionModal() {
  actionModalOpen.value = false
  selectedReport.value = null
  actionError.value = ''
}

function resolveDurationSeconds() {
  if (!actionNeedsDuration.value) return undefined
  if (actionForm.value.durationPreset !== 'custom') return Number(actionForm.value.durationPreset || 0) || undefined
  const n = Number(actionForm.value.durationSeconds || 0)
  return n > 0 ? n : undefined
}

async function submitAction() {
  actionError.value = ''
  const reportId = Number(selectedReport.value?.id || 0)
  if (!reportId) return
  const reason = String(actionForm.value.reason || '').trim()
  if (!reason) {
    actionError.value = '请填写处置理由'
    return
  }

  actionLoading.value = true
  try {
    await takeAction({
      reportId,
      action: actionForm.value.action,
      reason,
      durationSeconds: resolveDurationSeconds()
    })
    if (typeof window !== 'undefined' && window.$toast) {
      window.$toast({ type: 'success', title: '处置成功', text: '已记录审计并通知相关用户。' })
    }
    closeActionModal()
    await reload()
  } catch (e) {
    actionError.value = e?.message || '处置失败'
  } finally {
    actionLoading.value = false
  }
}
</script>

<style scoped>
.moderation-page {
  max-width: 1120px;
}

.moderation-shell {
  display: grid;
  gap: 14px;
}

.moderation-tabs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.moderation-state {
  margin-top: 12px;
}

.moderation-loading {
  padding: 16px;
}

.moderation-body {
  margin-top: 12px;
  display: grid;
  gap: 14px;
}

.moderation-filter-bar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 12px;
}

.moderation-filter-label {
  font-size: 12px;
}

.moderation-filter-select {
  width: 160px;
}

.moderation-filter-select :deep(.ui-select-trigger) {
  height: 32px;
}

.moderation-list {
  display: grid;
  gap: 10px;
}

.report-row:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.moderation-report-card,
.moderation-action-card {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--admin-surface) 70%, var(--surface) 30%);
}

.moderation-report-layout {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.moderation-report-main {
  min-width: 0;
  flex: 1;
}

.moderation-report-side {
  min-width: 160px;
  display: flex;
  justify-content: flex-end;
}

.moderation-report-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.moderation-status-badge {
  height: 18px;
  font-size: 11px;
}

.moderation-report-title {
  margin-top: 8px;
  font-weight: 800;
  color: var(--text-1);
}

.moderation-report-detail {
  margin-top: 6px;
  font-size: 12px;
  white-space: pre-wrap;
}

.moderation-report-meta {
  margin-top: 6px;
  font-size: 12px;
}

.moderation-action-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.moderation-pagination {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.moderation-modal {
  max-width: 680px;
}

.moderation-modal-body {
  padding: 16px;
  display: grid;
  gap: 12px;
}

.moderation-modal-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.moderation-modal-title {
  font-weight: 800;
}

.moderation-modal-meta,
.moderation-modal-label,
.moderation-modal-error {
  font-size: 12px;
}

.moderation-modal-field {
  display: grid;
  gap: 8px;
}

.moderation-modal-select {
  width: 100%;
}

.moderation-modal-select :deep(.ui-select-trigger) {
  height: 36px;
}

.moderation-modal-select--duration,
.moderation-modal-custom-duration {
  width: 200px;
}

.moderation-modal-duration {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.moderation-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .moderation-report-layout,
  .moderation-action-head {
    flex-direction: column;
    align-items: stretch;
  }

  .moderation-report-side {
    min-width: 0;
    justify-content: flex-start;
  }
}
</style>
