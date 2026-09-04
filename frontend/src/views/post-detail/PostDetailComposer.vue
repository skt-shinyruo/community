<template>
  <UiCard v-if="authed" class="comment-composer-card">
    <div class="comment-composer-head">
      <h2 class="comment-composer-title">发表评论</h2>
      <UiButton :disabled="composer.submitting" class="comment-composer-submit" @click="composer.submit">
        {{ composer.submitting ? '提交中…' : '提交' }}
      </UiButton>
    </div>

    <UiTextarea
      :model-value="composer.draft"
      placeholder="写下你的观点…（支持 Markdown）"
      aria-label="评论内容"
      :rows="4"
      :disabled="composer.submitting"
      @update:modelValue="composer.setDraft($event)"
    />
    <div v-if="composer.error" class="error comment-composer-error" role="alert">{{ composer.error }}</div>
  </UiCard>

  <UiCard v-else class="comment-composer-card">
    <UiState>
      登录后可点赞、评论、回复与关注。
      <template #actions>
        <UiButton @click="goLogin">去登录</UiButton>
      </template>
    </UiState>
  </UiCard>
</template>

<script setup>
import UiButton from '../../components/ui/UiButton.vue'
import UiCard from '../../components/ui/UiCard.vue'
import UiState from '../../components/ui/UiState.vue'
import UiTextarea from '../../components/ui/UiTextarea.vue'

defineProps({
  authed: Boolean,
  composer: {
    type: Object,
    required: true
  },
  goLogin: {
    type: Function,
    default: () => {}
  }
})
</script>

<style scoped src="./PostDetailComposer.css"></style>
