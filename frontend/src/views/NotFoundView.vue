<!-- 404 页面：未知路由兜底。 -->
<template>
  <div class="page reading">
    <UiState
      ref="stateRegion"
      variant="error"
      class="system-state"
      title="404 页面不存在"
      description="当前地址没有对应内容。你可以返回讨论区，重新进入有效页面。"
      tabindex="-1"
    >
      <template #actions>
        <UiButton variant="secondary" to="/posts">返回帖子列表</UiButton>
      </template>
    </UiState>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'

const stateRegion = ref(null)

onMounted(() => {
  stateRegion.value?.$el?.focus()
})
</script>

<style scoped>
.system-state:focus {
  outline: none;
}
</style>
