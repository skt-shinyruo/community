<template>
  <div class="page" style="max-width: 1100px; margin: 0 auto">
    <UiBreadcrumb />

    <UiCard>
      <UiPageHeader>
        <template #title>治理后台</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
        </template>
      </UiPageHeader>

      <div class="row" style="gap: 10px; flex-wrap: wrap; margin-top: 8px">
        <UiButton :variant="tab === 'reports' ? 'primary' : 'secondary'" @click="tab = 'reports'">举报队列</UiButton>
        <UiButton :variant="tab === 'actions' ? 'primary' : 'secondary'" @click="tab = 'actions'">处置审计</UiButton>
      </div>

      <UiEmpty v-if="error" type="error" style="margin-top: 12px">{{ error }}</UiEmpty>
      <div v-else-if="loading" class="muted" style="padding: 16px">加载中…</div>

      <div v-else style="margin-top: 12px">
        <template v-if="tab === 'reports'">
          <div class="row" style="gap: 10px; flex-wrap: wrap; align-items: center; margin-bottom: 12px">
            <div class="muted" style="font-size: 12px">状态</div>
            <select v-model="statusFilter" class="input" style="height: 32px; width: 160px">
              <option value="">全部</option>
              <option value="0">待处理</option>
              <option value="1">已处理</option>
              <option value="2">已驳回</option>
            </select>
          </div>

          <UiEmpty v-if="reports.length === 0">暂无举报</UiEmpty>

          <div v-else class="stack" style="gap: 10px">
            <div v-for="r in reports" :key="r.id" class="card flat report-row">
              <div class="row" style="justify-content: space-between; gap: 12px; align-items: flex-start">
                <div style="min-width: 0; flex: 1">
                  <div class="row" style="gap: 8px; flex-wrap: wrap; align-items: center">
                    <span class="tag">#{{ r.id }}</span>
                    <span class="tag">{{ targetTypeLabel(r.targetType) }} #{{ r.targetId }}</span>
                    <UiBadge v-if="Number(r.status) === 0" variant="warning" style="height: 18px; font-size: 11px">待处理</UiBadge>
                    <UiBadge v-else-if="Number(r.status) === 1" variant="success" style="height: 18px; font-size: 11px">已处理</UiBadge>
                    <UiBadge v-else variant="secondary" style="height: 18px; font-size: 11px">已驳回</UiBadge>
                  </div>

                  <div style="margin-top: 8px; font-weight: 800; color: var(--text-1)">{{ r.reason }}</div>
                  <div v-if="r.detail" class="muted" style="margin-top: 6px; font-size: 12px; white-space: pre-wrap">{{ r.detail }}</div>

                  <div class="muted" style="margin-top: 8px; font-size: 12px">
                    举报人：{{ r.reporterId }} · {{ formatTime(r.createTime) }}
                  </div>
                </div>

                <div class="stack" style="gap: 8px; min-width: 220px">
                  <UiButton variant="secondary" :disabled="actionLoading" @click="openActionModal(r)">处置</UiButton>
                </div>
              </div>
            </div>

            <div class="row" style="justify-content: center; margin-top: 8px">
              <UiButton v-if="reportsHasNext" variant="secondary" :disabled="loadingMore" @click="loadMoreReports">
                {{ loadingMore ? '加载中…' : '加载更多' }}
              </UiButton>
            </div>
          </div>
        </template>

        <template v-else>
          <UiEmpty v-if="actions.length === 0">暂无处置记录</UiEmpty>

          <div v-else class="stack" style="gap: 10px">
            <div v-for="a in actions" :key="a.id" class="card flat">
              <div class="row" style="justify-content: space-between; gap: 12px; align-items: center">
                <div class="row" style="gap: 10px; flex-wrap: wrap; align-items: center">
                  <span class="tag">#{{ a.id }}</span>
                  <span class="tag">report #{{ a.reportId }}</span>
                  <span class="tag">{{ a.action }}</span>
                </div>
                <div class="muted" style="font-size: 12px">{{ formatTime(a.createTime) }}</div>
              </div>
              <div style="margin-top: 8px; font-weight: 700">{{ a.reason }}</div>
              <div class="muted" style="margin-top: 6px; font-size: 12px">操作者：{{ a.actorId }} · 时长：{{ Number(a.durationSeconds || 0) }}s</div>
            </div>

            <div class="row" style="justify-content: center; margin-top: 8px">
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
    <div class="modal-card card" style="max-width: 680px">
      <div class="stack" style="padding: 16px; gap: 12px">
        <div class="row" style="justify-content: space-between; gap: 12px; align-items: center">
          <div style="font-weight: 800">处置举报 #{{ selectedReport?.id }}</div>
          <button class="btn-icon sm" type="button" aria-label="关闭" title="关闭" @click="closeActionModal">×</button>
        </div>

        <div class="muted" style="font-size: 12px">
          目标：{{ targetTypeLabel(selectedReport?.targetType) }} #{{ selectedReport?.targetId }}
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">动作</div>
          <select v-model="actionForm.action" class="input" style="height: 36px" :disabled="actionLoading">
            <option value="reject">驳回</option>
            <option value="hide">隐藏</option>
            <option value="delete">删除</option>
            <option value="warn">警告</option>
            <option value="mute">禁言</option>
            <option value="ban">封禁</option>
          </select>
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">理由（必填）</div>
          <UiTextarea v-model.trim="actionForm.reason" :rows="3" placeholder="简要说明处置原因" :disabled="actionLoading" />
        </div>

        <div v-if="actionNeedsDuration" class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">时长</div>
          <div class="row" style="gap: 10px; flex-wrap: wrap">
            <select v-model="actionForm.durationPreset" class="input" style="height: 36px; width: 200px" :disabled="actionLoading">
              <option value="3600">1 小时</option>
              <option value="86400">1 天</option>
              <option value="604800">7 天</option>
              <option value="2592000">30 天</option>
              <option value="custom">自定义</option>
            </select>

            <UiInput
              v-if="actionForm.durationPreset === 'custom'"
              v-model.trim="actionForm.durationSeconds"
              class="input"
              style="height: 36px; width: 200px"
              placeholder="秒，例如 600"
              :disabled="actionLoading"
            />
          </div>
        </div>

        <div v-if="actionError" class="error" style="font-size: 12px">{{ actionError }}</div>

        <div class="row" style="justify-content: flex-end; gap: 10px">
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
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
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

// actions
const actions = ref([])
const actionsPage = ref(0)
const actionsSize = 20
const actionsHasNext = ref(true)

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
.report-row:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}
</style>

