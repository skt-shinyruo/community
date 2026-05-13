<template>
  <div class="page user-management-page">
    <UiCard flat class="admin-page-header">
      <UiPageHeader>
        <template #title>用户管理</template>
        <template #subtitle>在明确理由、明确风险和明确审计责任的前提下授予或回收角色。</template>
      </UiPageHeader>
    </UiCard>

    <UiCard class="user-search-card">
      <div class="user-card-head">
        <div>
          <div class="user-card-eyebrow">Lookup</div>
          <div class="user-card-title">搜索用户</div>
        </div>
      </div>
      <div class="muted user-card-note">输入 `userId`、`username` 或 `email` 任意一个即可定位目标用户。</div>

      <div class="user-search-grid">
          <UiInput v-model.trim="qUserId" name="user-search-id" placeholder="userId" autocomplete="off" class="user-input user-input--id" />
          <UiInput v-model.trim="qUsername" name="user-search-username" placeholder="username" autocomplete="off" class="user-input user-input--name" />
          <UiInput v-model.trim="qEmail" name="user-search-email" placeholder="email" autocomplete="off" class="user-input user-input--email" />
          <UiButton variant="secondary" :disabled="loading" @click="onSearch">
            {{ loading ? '搜索中…' : '搜索' }}
          </UiButton>
      </div>

      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="success">{{ successMsg }}</div>
    </UiCard>

    <UiCard v-if="user" class="user-detail-card">
      <div class="user-card-head">
        <div>
          <div class="user-card-eyebrow">Target User</div>
          <div class="user-card-title">用户信息</div>
        </div>
      </div>

      <div class="user-summary-line muted">
          #{{ user.id }} · {{ user.username }} · {{ user.email || '（无邮箱）' }} · status={{ user.status }} · 当前角色：{{
            typeLabel(user.type)
          }}
      </div>

      <div class="user-role-grid">
        <div class="user-field">
          <div class="user-field-label">目标角色</div>
          <UiSelect
            v-model="nextType"
            name="user-next-role"
            class="user-select"
            aria-label="目标角色"
            :disabled="loading"
            :options="roleOptions"
          />
          <div class="muted user-field-help">
            提示：提升为 ADMIN 风险较高；建议填写明确原因并避免误操作。禁止降级自己（避免锁死管理入口）。
          </div>
        </div>

        <div class="user-field">
          <div class="user-field-label">审计原因（必填）</div>
          <UiInput v-model.trim="reason" name="user-role-reason" placeholder="例如：自托管初始管理员 / 运营团队授予版主权限" autocomplete="off" />
        </div>
      </div>

      <div class="user-actions">
        <UiButton variant="secondary" :disabled="loading" @click="openConfirm">提交变更</UiButton>
      </div>
    </UiCard>

    <UiModalConfirm
      v-if="confirmOpen"
      title="确认修改角色"
      :message="confirmMessage"
      confirm-text="确认"
      confirm-variant="danger"
      @cancel="confirmOpen = false"
      @confirm="onConfirmUpdate"
    />
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import { adminSearchUser, adminUpdateUserRole } from '../api/services/adminUserService'

const emit = defineEmits(['trace'])
const showToast = inject('showToast', () => {})

const qUserId = ref('')
const qUsername = ref('')
const qEmail = ref('')

const loading = ref(false)
const error = ref('')
const successMsg = ref('')

const user = ref(null)
const nextType = ref(0)
const reason = ref('')
const confirmOpen = ref(false)
const roleOptions = [
  { label: 'USER（普通用户）', value: 0 },
  { label: 'MODERATOR（版主）', value: 2 },
  { label: 'ADMIN（管理员）', value: 1 }
]

function typeLabel(t) {
  const v = Number(t || 0)
  if (v === 1) return 'ADMIN'
  if (v === 2) return 'MODERATOR'
  return 'USER'
}

const confirmMessage = computed(() => {
  if (!user.value) return '是否继续？'
  const from = typeLabel(user.value.type)
  const to = typeLabel(nextType.value)
  return `成员 ${user.value.username}（#${user.value.id}）角色变更：${from} -> ${to}；原因：${reason.value || '（空）'}`
})

async function onSearch() {
  error.value = ''
  successMsg.value = ''
  user.value = null

  const hasAny = !!qUserId.value.trim() || !!qUsername.value.trim() || !!qEmail.value.trim()
  if (!hasAny) {
    error.value = '请至少输入 userId / username / email 之一'
    return
  }

  loading.value = true
  try {
    const { data, traceId } = await adminSearchUser({ userId: qUserId.value, username: qUsername.value, email: qEmail.value })
    emit('trace', traceId || '')
    if (!data) {
      successMsg.value = '未找到用户'
      return
    }
    user.value = data
    nextType.value = Number(data.type || 0)
    reason.value = ''
  } catch (e) {
    error.value = e?.message || '搜索失败'
  } finally {
    loading.value = false
  }
}

function openConfirm() {
  error.value = ''
  successMsg.value = ''
  if (!user.value) return
  if (!reason.value.trim()) {
    error.value = '请填写原因'
    return
  }
  confirmOpen.value = true
}

async function onConfirmUpdate() {
  confirmOpen.value = false
  error.value = ''
  successMsg.value = ''
  if (!user.value) return

  loading.value = true
  try {
    const { traceId } = await adminUpdateUserRole({
      targetUserId: user.value.id,
      type: nextType.value,
      reason: reason.value,
      confirm: true
    })
    emit('trace', traceId || '')

    user.value = { ...user.value, type: Number(nextType.value || 0) }
    successMsg.value = '角色已更新（用户需重新登录/刷新 token 后生效）。'
    showToast({ type: 'success', title: '已更新', text: successMsg.value })
  } catch (e) {
    error.value = e?.message || '更新失败'
    showToast({ type: 'error', title: '更新失败', text: error.value })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.user-management-page {
  max-width: 920px;
}

.user-search-card,
.user-detail-card {
  display: grid;
  gap: 14px;
}

.user-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.user-card-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-3);
  margin-bottom: 4px;
}

.user-card-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-1);
}

.user-card-note,
.user-summary-line {
  font-size: 13px;
}

.user-search-grid {
  display: grid;
  grid-template-columns: 140px 200px minmax(220px, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.user-role-grid {
  display: grid;
  gap: 14px;
}

.user-field {
  display: grid;
  gap: 8px;
}

.user-field-label {
  font-size: 12px;
  color: var(--text-3);
}

.user-select {
  max-width: 260px;
}

.user-select :deep(.ui-select-trigger) {
  height: var(--control-height);
}

.user-field-help {
  font-size: 12px;
}

.user-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .user-search-grid {
    grid-template-columns: 1fr;
  }
}
</style>
