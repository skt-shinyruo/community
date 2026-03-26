<!-- 编辑弹窗：用于帖子/评论的窗口内编辑。 -->
<template>
  <div class="modal-mask" @click.self="$emit('close')">
    <div class="modal-card card" style="max-width: 720px">
      <div class="stack" style="padding: 16px; gap: 12px">
        <div class="row" style="justify-content: space-between; gap: 12px; align-items: center">
          <div style="font-weight: 800">{{ headerTitle }}</div>
          <UiIconButton aria-label="关闭" title="关闭" size="sm" @click="$emit('close')">×</UiIconButton>
        </div>

        <div v-if="mode === 'post'" class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">标题</div>
          <UiInput v-model.trim="title" class="input" placeholder="标题" :disabled="loading" />
        </div>

        <div class="stack" style="gap: 8px">
          <div class="muted" style="font-size: 12px">内容</div>
          <UiTextarea
            v-model.trim="content"
            :rows="mode === 'post' ? 10 : 6"
            placeholder="支持 Markdown"
            :disabled="loading"
          />
        </div>

        <div v-if="error" class="error" style="font-size: 12px">{{ error }}</div>

        <div class="row" style="justify-content: flex-end; gap: 10px">
          <UiButton variant="secondary" :disabled="loading" @click="$emit('close')">取消</UiButton>
          <UiButton :disabled="loading" @click="submit">{{ loading ? '保存中…' : '保存' }}</UiButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import UiButton from '../ui/UiButton.vue'
import UiIconButton from '../ui/UiIconButton.vue'
import UiInput from '../ui/UiInput.vue'
import UiTextarea from '../ui/UiTextarea.vue'

const props = defineProps({
  mode: { type: String, default: 'post' }, // post | comment
  loading: { type: Boolean, default: false },
  initialTitle: { type: String, default: '' },
  initialContent: { type: String, default: '' }
})

const emit = defineEmits(['close', 'submit'])

const title = ref(String(props.initialTitle || ''))
const content = ref(String(props.initialContent || ''))
const error = ref('')

watch(
  () => props.initialTitle,
  (v) => {
    title.value = String(v || '')
  }
)
watch(
  () => props.initialContent,
  (v) => {
    content.value = String(v || '')
  }
)

const headerTitle = computed(() => (props.mode === 'comment' ? '编辑评论' : '编辑帖子'))

async function submit() {
  error.value = ''
  const payload = {
    title: title.value,
    content: content.value
  }
  if (props.mode === 'post' && !String(payload.title || '').trim()) {
    error.value = '标题不能为空'
    return
  }
  if (!String(payload.content || '').trim()) {
    error.value = '内容不能为空'
    return
  }
  emit('submit', payload)
}
</script>
