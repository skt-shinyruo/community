<!-- 确认弹窗：用于删除/置顶/加精等危险操作二次确认。外壳与焦点行为收敛到 UiModal。 -->
<template>
  <UiModal :title="title" :busy="busy" @close="$emit('cancel')">
    <p class="ui-modal-confirm__message">{{ message }}</p>
    <template #footer>
      <UiButton variant="secondary" :disabled="busy" @click="$emit('cancel')">取消</UiButton>
      <UiButton :variant="confirmVariant" :disabled="busy" @click="$emit('confirm')">{{ confirmText }}</UiButton>
    </template>
  </UiModal>
</template>

<script setup>
import UiButton from './UiButton.vue'
import UiModal from './UiModal.vue'

defineProps({
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '是否继续？' },
  confirmText: { type: String, default: '确认' },
  confirmVariant: { type: String, default: 'primary' }, // primary | danger
  busy: { type: Boolean, default: false }
})

defineEmits(['confirm', 'cancel'])
</script>

<style scoped>
.ui-modal-confirm__message {
  margin: 0;
  color: var(--text-3);
}
</style>
