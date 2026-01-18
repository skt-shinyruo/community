<!-- 确认弹窗：用于删除/置顶/加精等危险操作二次确认。 -->
<template>
  <div class="modal-mask" @click.self="$emit('cancel')">
    <div class="modal-card card">
      <div class="stack">
        <div style="font-weight: 700">{{ title }}</div>
        <div class="muted">{{ message }}</div>
        <div class="row" style="justify-content: flex-end">
          <button class="btn secondary" @click="$emit('cancel')">取消</button>
          <button class="btn" @click="$emit('confirm')">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '是否继续？' }
})

defineEmits(['confirm', 'cancel'])
</script>

