<!-- 确认弹窗：用于删除/置顶/加精等危险操作二次确认。 -->
<template>
  <dialog
    ref="dialogRef"
    class="modal-mask"
    role="dialog"
    aria-modal="true"
    :aria-labelledby="titleId"
    :aria-describedby="descriptionId"
    @click.self="$emit('cancel')"
    @cancel.prevent="$emit('cancel')"
  >
    <div
      class="modal-card card"
    >
      <div class="stack">
        <div :id="titleId" style="font-weight: 700">{{ title }}</div>
        <div :id="descriptionId" class="muted">{{ message }}</div>
        <div class="row" style="justify-content: flex-end">
          <UiButton variant="secondary" @click="$emit('cancel')">取消</UiButton>
          <UiButton :variant="confirmVariant" @click="$emit('confirm')">{{ confirmText }}</UiButton>
        </div>
      </div>
    </div>
  </dialog>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'
import UiButton from './UiButton.vue'

defineProps({
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '是否继续？' },
  confirmText: { type: String, default: '确认' },
  confirmVariant: { type: String, default: 'primary' } // primary | danger
})

defineEmits(['confirm', 'cancel'])

const uid = useId()
const titleId = `ui-modal-confirm-title-${uid}`
const descriptionId = `ui-modal-confirm-description-${uid}`
const dialogRef = ref(null)
onMounted(() => dialogRef.value?.showModal?.())
onBeforeUnmount(() => dialogRef.value?.close?.())
</script>
