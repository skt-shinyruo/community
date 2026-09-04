<!-- 举报弹窗：用于帖子/评论/用户的举报提交。外壳与焦点行为收敛到 UiModal。 -->
<template>
  <UiModal title="举报" size="md" :busy="submitting" @close="$emit('close')">
    <div class="report-modal-body">
      <p class="report-modal-target">目标：{{ targetTypeLabel }} #{{ normalizeOpaqueId(targetId) || '-' }}</p>

      <UiField label="原因">
        <template #default="{ controlId }">
          <select
            :id="controlId"
            v-model="reason"
            name="report-reason"
            class="report-reason-select"
            :disabled="submitting"
          >
            <option
              v-for="option in reasonOptions"
              :key="String(option.value)"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </option>
          </select>
        </template>
      </UiField>

      <UiField label="补充说明（可选）">
        <UiTextarea
          v-model.trim="detail"
          name="report-detail"
          :rows="4"
          placeholder="请描述具体情况（例如：违规内容位置、截图说明等）"
          :disabled="submitting"
        />
      </UiField>

      <p v-if="error" class="error report-modal-error" role="alert">{{ error }}</p>
    </div>

    <template #footer>
      <UiButton variant="secondary" :disabled="submitting" @click="$emit('close')">取消</UiButton>
      <UiButton :disabled="submitting || !reason" @click="submit">
        {{ submitting ? '提交中…' : '提交举报' }}
      </UiButton>
    </template>
  </UiModal>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import UiButton from '../ui/UiButton.vue'
import UiField from '../ui/UiField.vue'
import UiModal from '../ui/UiModal.vue'
import UiTextarea from '../ui/UiTextarea.vue'
import { createReport } from '../../api/services/reportService'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { showToast } from '../../ui/toastService'

const props = defineProps({
  targetType: { type: String, required: true }, // post | comment | user
  targetId: { type: [String, Number], required: true }
})

const emit = defineEmits(['close', 'submitted'])
const auth = useAuthStore()

const reasonOptions = [
  { label: '垃圾广告', value: '垃圾广告' },
  { label: '人身攻击', value: '人身攻击' },
  { label: '色情低俗', value: '色情低俗' },
  { label: '违法信息', value: '违法信息' },
  { label: '侵权/盗版', value: '侵权/盗版' },
  { label: '其他', value: '其他' }
]

const reason = ref(reasonOptions[0]?.value || '')
const detail = ref('')
const submitting = ref(false)
const error = ref('')
let operationId = 0
let disposed = false

function isCurrentOperation(id, authGeneration, targetType, targetId) {
  return !disposed
    && id === operationId
    && auth.tokenGeneration === authGeneration
    && String(props.targetType || '') === targetType
    && normalizeOpaqueId(props.targetId) === targetId
}

const targetTypeLabel = computed(() => {
  const t = String(props.targetType || '').toLowerCase()
  if (t === 'post') return '帖子'
  if (t === 'comment') return '评论'
  if (t === 'user') return '用户'
  return t || '目标'
})

async function submit() {
  const id = ++operationId
  const authGeneration = auth.tokenGeneration
  const targetType = String(props.targetType || '')
  const targetId = normalizeOpaqueId(props.targetId)
  error.value = ''
  submitting.value = true
  try {
    await createReport({
      targetType,
      targetId,
      reason: reason.value,
      detail: detail.value
    })
    if (!isCurrentOperation(id, authGeneration, targetType, targetId)) return
    showToast({ type: 'success', title: '已提交', text: '感谢反馈，我们会尽快处理。' })
    detail.value = ''
  } catch (e) {
    if (!isCurrentOperation(id, authGeneration, targetType, targetId)) return
    error.value = e?.message || '提交失败'
    return
  } finally {
    if (isCurrentOperation(id, authGeneration, targetType, targetId)) submitting.value = false
  }

  emit('submitted')
  emit('close')
}

watch(
  () => auth.tokenGeneration,
  () => {
    operationId += 1
    submitting.value = false
    error.value = ''
    detail.value = ''
    emit('close')
  }
)

watch(
  () => [String(props.targetType || ''), normalizeOpaqueId(props.targetId)],
  () => {
    operationId += 1
    submitting.value = false
    error.value = ''
    detail.value = ''
  }
)

onBeforeUnmount(() => {
  disposed = true
  operationId += 1
})
</script>

<style scoped>
.report-modal-body {
  display: grid;
  gap: var(--space-3);
}

.report-modal-target {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-xs);
}

.report-reason-select {
  width: 100%;
  height: var(--control-height);
  padding: 0 var(--control-padding-x);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  outline: none;
  background: var(--bg);
  color: var(--text-1);
  font-size: var(--text-sm);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.report-reason-select:hover:not(:disabled) {
  border-color: var(--border-strong);
}

.report-reason-select:focus-visible {
  border-color: var(--accent);
  box-shadow: var(--focus-ring);
}

.report-reason-select:disabled {
  background: var(--surface-2);
  color: var(--muted);
  cursor: not-allowed;
}

.report-modal-error {
  margin: 0;
  font-size: var(--text-xs);
}
</style>
