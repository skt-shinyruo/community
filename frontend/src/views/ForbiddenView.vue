<!-- 无权限页面：用于路由守卫的体验层拦截。 -->
<template>
  <div class="page reading">
    <UiState
      ref="stateRegion"
      variant="error"
      class="system-state"
      title="403 无权限"
      description="你当前没有访问该页面的权限。最终结果仍以服务端鉴权为准。"
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
