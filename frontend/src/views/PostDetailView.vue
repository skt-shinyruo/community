<template>
  <div class="page reading">
    <UiCard class="post-detail-shell">
      <div class="post-detail-head">
        <div class="post-detail-breadcrumb">
          <UiBreadcrumb />
        </div>
        <div class="post-detail-shell-actions">
          <UiButton variant="ghost" class="post-detail-back" @click="$router.back()">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            返回
          </UiButton>
          <UiButton variant="secondary" :disabled="page.loading" @click="page.reload">{{ page.loading ? '加载中…' : '刷新' }}</UiButton>
        </div>
      </div>

      <div v-if="page.error" class="error post-detail-state">{{ page.error }}</div>
      <div v-else-if="page.loading" class="muted post-detail-state">加载中…</div>
      <UiState v-else-if="!page.post" class="post-detail-state">暂无数据</UiState>

      <div v-else class="post-detail-layout">
        <article class="post-article-card">
          <div class="post-article-frame">
            <div class="post-article-head">
              <div class="post-article-vote">
                <div class="post-article-vote-label">Audience</div>
                <div class="vote-box-detail">
                  <UiIconButton class="vote-btn-d up" :class="{ active: page.post.liked }" aria-label="点赞" @click="postActions.toggleLike">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 19V5M5 12l7-7 7 7"/></svg>
                  </UiIconButton>
                  <span class="vote-count-d">{{ page.post.likeCount || 0 }}</span>
                </div>
              </div>

              <div class="post-article-main">
                <div class="post-article-kicker">
                  <span class="post-article-kicker-label">讨论线程</span>
                  <span class="post-article-kicker-meta">帖子 #{{ page.post.id || page.postId }}</span>
                </div>

                <div class="post-article-taxonomy">
                  <UiBadge v-if="page.post.type === 1" variant="accent">置顶</UiBadge>
                  <UiBadge v-if="page.post.status === 1" variant="success">加精</UiBadge>
                  <UiBadge v-if="page.post.status === 2" variant="danger">已删除</UiBadge>

                  <RouterLink
                    v-if="page.post.categoryId"
                    class="taxonomy-link"
                    :to="{ name: 'posts', query: { categoryId: String(page.post.categoryId) } }"
                    :title="`查看分类 ${page.categoryLabel(page.post.categoryId)}`"
                  >
                    <span class="tag topic-category">{{ page.categoryLabel(page.post.categoryId) }}</span>
                  </RouterLink>

                  <RouterLink
                    v-for="t in (Array.isArray(page.post.tags) ? page.post.tags : [])"
                    :key="t"
                    class="taxonomy-link"
                    :to="{ name: 'posts', query: { tag: t } }"
                    :title="`查看标签 ${t}`"
                  >
                    <span class="tag">#{{ t }}</span>
                  </RouterLink>
                </div>

                <h1 class="post-article-title">{{ page.post.title }}</h1>

                <div class="post-article-meta">
                  <UiUserCard :user="page.postAuthor">
                    <div class="post-article-author">
                      <UiAvatar :src="page.postAuthor?.headerUrl || ''" :name="page.postAuthor?.username || ''" :size="20" />
                      <span class="post-article-author-name">{{ page.postAuthor?.username || `成员 ${page.post.userId}` }}</span>
                    </div>
                  </UiUserCard>

                  <UiRoleBadge :user="page.postAuthor" size="md" />

                  <span>发布于 {{ formatTime(page.post.createTime) }}</span>
                  <span v-if="Number(page.post.editCount || 0) > 0" :title="page.post.updateTime ? formatTime(page.post.updateTime) : ''">· 已编辑</span>
                </div>

                <div class="post-article-ledger">
                  <div class="post-ledger-item">
                    <span class="post-ledger-label">赞同</span>
                    <strong>{{ page.post.likeCount || 0 }}</strong>
                  </div>
                  <div class="post-ledger-item">
                    <span class="post-ledger-label">回复</span>
                    <strong>{{ page.post.commentCount || 0 }}</strong>
                  </div>
                  <div class="post-ledger-item">
                    <span class="post-ledger-label">当前状态</span>
                    <strong>{{ Number(page.post.editCount || 0) > 0 ? '持续更新' : '等待回应' }}</strong>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="divider"></div>

          <div class="post-article-body">
            <PostBlockRenderer :blocks="page.post.blocks" />
          </div>

          <PostDetailActions
            :authed="page.authed"
            :post="page.post"
            :actions="postActions"
          />
        </article>
        <PostDetailComments
          :post="page.post"
          :discussion="discussion"
          :comment-editing="postActions.commentEditing"
        />
      </div>
    </UiCard>

    <PostDetailComposer :authed="page.authed" :composer="discussion.composer" />
  </div>
</template>

<script setup>
import UiCard from '../components/ui/UiCard.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiUserCard from '../components/ui/UiUserCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiIconButton from '../components/ui/UiIconButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import PostBlockRenderer from '../components/posts/PostBlockRenderer.vue'
import PostDetailActions from './post-detail/PostDetailActions.vue'
import PostDetailComments from './post-detail/PostDetailComments.vue'
import PostDetailComposer from './post-detail/PostDetailComposer.vue'
import { formatTime } from '../utils/time'
import { usePostDetailLoader } from './post-detail/usePostDetailLoader'

const emit = defineEmits(['trace'])
const { page, postActions, discussion } = usePostDetailLoader(emit)
</script>

<style scoped src="./post-detail/PostDetailView.css"></style>
