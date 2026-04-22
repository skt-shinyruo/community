<!-- 举报弹窗：用于帖子/评论/用户的举报提交。 -->
<template>
  <div class="modal-mask" @click.self="$emit('close')">
    <div class="modal-card card" style="max-width: 560px">
      <div class="stack" style="padding: 16px">
        <div class="row" style="justify-content: space-between; gap: 12px; align-items: center">
          <div style="font-weight: 800">举报</div>
          <UiIconButton aria-label="关闭" title="关闭" size="sm" @click="$emit('close')">×</UiIconButton>
        </div>

        <div class="muted" style="font-size: 12px">
          目标：{{ targetTypeLabel }} #{{ normalizeOpaqueId(targetId) || '-' }}
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">原因</div>
          <UiSelect
            v-model="reason"
            name="report-reason"
            class="report-reason-select"
            :disabled="submitting"
            :options="reasonOptions"
          />
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">补充说明（可选）</div>
          <UiTextarea
            v-model.trim="detail"
            name="report-detail"
            :rows="4"
            placeholder="请描述具体情况（例如：违规内容位置、截图说明等）"
            :disabled="submitting"
          />
        </div>

        <div v-if="error" class="error" style="font-size: 12px">{{ error }}</div>

        <div class="row" style="justify-content: flex-end; gap: 10px">
          <UiButton variant="secondary" :disabled="submitting" @click="$emit('close')">取消</UiButton>
          <UiButton :disabled="submitting || !reason" @click="submit">
            {{ submitting ? '提交中…' : '提交举报' }}
          </UiButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiIconButton from '../ui/UiIconButton.vue'
import UiSelect from '../ui/UiSelect.vue'
import UiTextarea from '../ui/UiTextarea.vue'
import { createReport } from '../../api/services/reportService'
import { normalizeOpaqueId } from '../../utils/opaqueId'

const props = defineProps({
  targetType: { type: String, required: true }, // post | comment | user
  targetId: { type: [String, Number], required: true }
})

const emit = defineEmits(['close', 'submitted'])

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

const targetTypeLabel = computed(() => {
  const t = String(props.targetType || '').toLowerCase()
  if (t === 'post') return '帖子'
  if (t === 'comment') return '评论'
  if (t === 'user') return '用户'
  return t || '目标'
})

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await createReport({
      targetType: props.targetType,
      targetId: normalizeOpaqueId(props.targetId),
      reason: reason.value,
      detail: detail.value
    })
    if (typeof window !== 'undefined' && window.$toast) {
      window.$toast({ type: 'success', title: '已提交', text: '感谢反馈，我们会尽快处理。' })
    }
    detail.value = ''
  } catch (e) {
    error.value = e?.message || '提交失败'
    return
  } finally {
    submitting.value = false
  }

  emit('submitted')
  emit('close')
}
</script>

<style scoped>
.report-reason-select {
  width: 100%;
}

.report-reason-select :deep(.ui-select-trigger) {
  width: 100%;
}
</style>
