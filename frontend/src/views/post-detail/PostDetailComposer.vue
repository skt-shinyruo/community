<template>
  <UiCard v-if="authed" class="comment-composer-card">
    <UiPageHeader>
      <template #title>发表评论</template>
      <template #subtitle>参与讨论 · 支持回复树</template>
      <template #actions>
        <UiButton :disabled="composer.submitting" @click="composer.submit">
          {{ composer.submitting ? '提交中…' : '提交' }}
        </UiButton>
      </template>
    </UiPageHeader>

    <div class="stack comment-composer">
      <UiTextarea
        :model-value="composer.draft"
        :model-modifiers="{ trim: true }"
        placeholder="写下你的观点…（支持 Markdown）"
        :rows="4"
        @update:modelValue="composer.setDraft"
      />
      <div v-if="composer.error" class="error">{{ composer.error }}</div>
    </div>
  </UiCard>

  <UiCard v-else>
    <UiState>登录后可点赞、评论、回复与关注。</UiState>
  </UiCard>
</template>

<script setup>
import UiButton from '../../components/ui/UiButton.vue'
import UiCard from '../../components/ui/UiCard.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiState from '../../components/ui/UiState.vue'
import UiTextarea from '../../components/ui/UiTextarea.vue'

defineProps({
  authed: Boolean,
  composer: {
    type: Object,
    required: true
  }
})
</script>

<style scoped src="./PostDetailComposer.css"></style>
