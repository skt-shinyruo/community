<template>
  <div class="page relations-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>{{ policy.title }}</template>
      <template #subtitle>{{ policy.subtitle }}</template>
      <template #actions>
        <UiButton variant="secondary" class="relations-refresh" :disabled="loading || loadingMore" @click="reload">刷新</UiButton>
      </template>
    </UiPageHeader>

    <div class="relations-feed">
      <div v-if="loading && items.length === 0" class="relations-skeletons">
        <UiSkeleton variant="list" :rows="4" />
      </div>

      <UiState v-if="error && items.length === 0" variant="error">
        {{ error }}
        <template #description>{{ policy.errorDescription }}</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">重试</UiButton>
        </template>
      </UiState>
      <div v-else-if="error" class="error relations-inline-error">{{ error }}</div>

      <UiState v-if="!loading && !error && items.length === 0">
        {{ policy.emptyTitle }}
        <template #description>{{ policy.emptyDescription }}</template>
        <template #actions>
          <UiButton :to="{ name: 'posts' }">回到讨论区</UiButton>
          <UiButton variant="ghost" :to="{ name: 'userProfile', params: { userId: String(userId || '') } }">返回主页</UiButton>
        </template>
      </UiState>

      <div v-if="items.length > 0" class="relations-list">
        <article
          v-for="it in items"
          :key="it.targetId"
          class="relation-card"
          role="link"
          tabindex="0"
          @keydown.enter="onCardEnter($event, it)"
          @click="openProfile(it)"
        >
          <div class="relation-main">
            <UiAvatar :src="it.user?.headerUrl || ''" :name="it.user?.username || ''" :size="44" />
            <div class="relation-copy">
              <div class="relation-name-row">
                <RouterLink :to="`/users/${it.targetId}`" class="relation-name" @click.stop>
                  {{ it.user?.username || '社区成员' }}
                </RouterLink>
                <UiBadge variant="accent">{{ policy.pill }}</UiBadge>
              </div>
              <div class="relation-summary">{{ policy.summary }}</div>
              <div class="relation-meta">建立关系于 {{ formatTime(it.followTime) }}</div>
            </div>
          </div>

          <div class="relation-actions" v-if="authed && meId !== it.targetId" @click.stop>
            <UiButton v-if="!it.hasFollowed" :disabled="isMutating(it.targetId)" @click="doFollow(it)">关注</UiButton>
            <UiButton variant="secondary" v-else :disabled="isMutating(it.targetId)" @click="doUnfollow(it)">取关</UiButton>
          </div>
        </article>
      </div>

      <div v-if="pageError" class="error relations-inline-error">{{ pageError }}</div>

      <div v-if="loadingMore || (hasNext && items.length > 0)" class="relations-load-more">
        <UiButton v-if="loadingMore" variant="ghost" disabled>
          <LoaderCircle :size="14" aria-hidden="true" class="relations-load-more-spinner" />
          正在加载…
        </UiButton>
        <UiButton v-else variant="secondary" class="relations-load-more-btn" @click="loadMore">加载更多</UiButton>
      </div>
      <div v-if="!hasNext && items.length > 0" class="relations-end-note">已经到底了</div>
    </div>
  </div>
</template>

<script setup>
import { toRef } from 'vue'
import { LoaderCircle } from 'lucide-vue-next'
import { formatTime } from '../utils/time'
import { useFollowRelationListState } from './followRelation/useFollowRelationListState'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'

const props = defineProps({
  relationKind: {
    type: String,
    required: true,
    validator: (value) => value === 'followees' || value === 'followers'
  },
  userId: String
})

const {
  authed,
  doFollow,
  doUnfollow,
  error,
  hasNext,
  isMutating,
  items,
  load,
  loading,
  loadingMore,
  loadMore,
  meId,
  nextCursor,
  openProfile,
  pageError,
  policy,
  reload
} = useFollowRelationListState({
  relationKind: toRef(props, 'relationKind'),
  profileUserId: toRef(props, 'userId')
})

// 键盘打开只响应卡片自身获得焦点时的 Enter；嵌套链接（成员名）与关注按钮的
// Enter 走原生行为，不重复触发打开。
function onCardEnter(event, item) {
  if (event?.target !== event?.currentTarget) return
  openProfile(item)
}

defineExpose({ load, loadMore, nextCursor, reload })
</script>

<style scoped>
.relations-page {
  max-width: 980px;
}

.relations-refresh {
  flex-shrink: 0;
}

.relations-feed {
  display: grid;
  gap: var(--space-3);
}

.relations-skeletons {
  display: grid;
  gap: var(--space-3);
}

.relations-inline-error {
  font-size: var(--text-sm);
}

.relations-list {
  display: grid;
  gap: var(--space-3);
}

.relation-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.relation-card:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.relation-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.relation-main {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  min-width: 0;
  flex: 1;
}

.relation-copy {
  min-width: 0;
  display: grid;
  gap: var(--space-1);
}

.relation-name-row {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
}

.relation-name {
  font-weight: 700;
  color: var(--text-1);
  text-decoration: none;
  overflow-wrap: anywhere;
  word-break: break-word;
  border-radius: var(--radius-sm);
}

.relation-name:hover {
  color: var(--link-color);
}

.relation-name:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.relation-summary {
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.relation-meta {
  font-size: 13px;
  color: var(--text-3);
}

.relation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  flex: none;
}

.relations-load-more {
  display: flex;
  justify-content: center;
  padding-top: var(--space-2);
}

.relations-load-more-btn {
  min-width: 260px;
}

.relations-load-more-spinner {
  animation: relations-load-more-spin 0.8s linear infinite;
}

@keyframes relations-load-more-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .relations-load-more-spinner {
    animation: none;
  }
}

.relations-end-note {
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 768px) {
  .relation-card {
    flex-direction: column;
    align-items: stretch;
  }

  .relation-actions {
    align-self: flex-start;
  }

  .relations-load-more-btn {
    min-width: 0;
    width: 100%;
  }
}
</style>
