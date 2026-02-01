<template>
  <div class="page" style="max-width: 860px; margin: 0 auto">
    <UiCard flat>
      <UiPageHeader>
        <template #title>用户管理</template>
        <template #subtitle>授予/回收角色（仅管理员）。操作会记录审计日志。</template>
      </UiPageHeader>
    </UiCard>

    <UiCard style="margin-top: 12px">
      <div class="stack" style="gap: 12px">
        <div style="font-weight: 800">搜索用户</div>
        <div class="muted" style="font-size: 12px">输入 userId / username / email 任意一个即可。</div>

        <div class="row" style="gap: 8px; flex-wrap: wrap">
          <UiInput v-model.trim="qUserId" placeholder="userId" autocomplete="off" style="max-width: 140px" />
          <UiInput v-model.trim="qUsername" placeholder="username" autocomplete="off" style="max-width: 200px" />
          <UiInput v-model.trim="qEmail" placeholder="email" autocomplete="off" style="max-width: 260px" />
          <UiButton variant="secondary" :disabled="loading" @click="onSearch">
            {{ loading ? '搜索中…' : '搜索' }}
          </UiButton>
        </div>

        <div v-if="error" class="error">{{ error }}</div>
        <div v-if="successMsg" class="success">{{ successMsg }}</div>
      </div>
    </UiCard>

    <UiCard v-if="user" style="margin-top: 12px">
      <div class="stack" style="gap: 12px">
        <div style="font-weight: 800">用户信息</div>
        <div class="muted" style="font-size: 12px">
          #{{ user.id }} · {{ user.username }} · {{ user.email || '（无邮箱）' }} · status={{ user.status }} · 当前角色：{{
            typeLabel(user.type)
          }}
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">目标角色</div>
          <select v-model="nextType" class="input" :disabled="loading" style="max-width: 240px">
            <option :value="0">USER（普通用户）</option>
            <option :value="2">MODERATOR（版主）</option>
            <option :value="1">ADMIN（管理员）</option>
          </select>
          <div class="muted" style="font-size: 12px">
            提示：提升为 ADMIN 风险较高；建议填写明确原因并避免误操作。禁止降级自己（避免锁死管理入口）。
          </div>
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">原因（必填）</div>
          <UiInput v-model.trim="reason" placeholder="例如：自托管初始管理员 / 运营团队授予版主权限" autocomplete="off" />
        </div>

        <div class="row" style="justify-content: flex-end; gap: 8px; flex-wrap: wrap">
          <UiButton variant="secondary" :disabled="loading" @click="openConfirm">提交变更</UiButton>
        </div>
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
  return `用户 #${user.value.id}（${user.value.username}）角色变更：${from} -> ${to}；原因：${reason.value || '（空）'}`
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

