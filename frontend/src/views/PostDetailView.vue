<template>
  <div class="page reading post-detail-page">
    <nav class="post-detail-nav" aria-label="页面层级">
      <UiButton variant="ghost" class="post-detail-back" :to="{ name: 'posts' }">
        <ArrowLeft :size="16" aria-hidden="true" />
        返回帖子列表
      </UiButton>
    </nav>

    <div v-if="page.loading && !page.post" class="post-detail-skeletons">
      <UiSkeleton variant="detail" />
      <UiSkeleton variant="list" :rows="3" />
    </div>

    <UiState v-else-if="page.error && !page.post" variant="error" class="post-detail-state" :title="page.error">
      <template #description>帖子详情加载失败，可以重试或返回帖子列表。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="page.loading" @click="page.reload">重试</UiButton>
      </template>
    </UiState>

    <UiState v-else-if="!page.post" class="post-detail-state">
      帖子不存在或已删除
      <template #description>返回帖子列表，看看社区里正在发生的其他讨论。</template>
      <template #actions>
        <UiButton variant="secondary" :to="{ name: 'posts' }">返回帖子列表</UiButton>
      </template>
    </UiState>

    <template v-else>
      <div v-if="page.error" class="error post-detail-inline-error" role="alert">{{ page.error }}</div>

      <article class="post-article-card">
        <header class="post-article-head">
          <div class="post-article-taxonomy">
            <UiBadge v-if="page.post.type === 1" variant="accent">置顶</UiBadge>
            <UiBadge v-if="page.post.status === 1" variant="success">加精</UiBadge>
            <UiBadge v-if="page.post.status === 2" variant="danger">已删除</UiBadge>

            <RouterLink
              v-if="page.post.categoryId"
              class="post-article-category"
              :to="{ name: 'posts', query: { categoryId: String(page.post.categoryId) } }"
              :title="`查看分类 ${page.categoryLabel(page.post.categoryId)}`"
            >
              {{ page.categoryLabel(page.post.categoryId) }}
            </RouterLink>

            <RouterLink
              v-for="t in (Array.isArray(page.post.tags) ? page.post.tags : [])"
              :key="t"
              class="post-article-tag"
              :to="{ name: 'posts', query: { tag: t } }"
              :title="`查看标签 ${t}`"
            >
              #{{ t }}
            </RouterLink>
          </div>

          <h1 class="post-article-title">{{ page.post.title }}</h1>

          <div class="post-article-meta">
            <UiUserCard :user="page.postAuthor">
              <RouterLink class="post-article-author" :to="`/users/${page.post.userId}`">
                <UiAvatar :src="page.postAuthor?.headerUrl || ''" :name="page.postAuthor?.username || ''" :size="20" />
                <span class="post-article-author-name">{{ page.postAuthor?.username || `成员 ${page.post.userId}` }}</span>
              </RouterLink>
            </UiUserCard>

            <UiRoleBadge :user="page.postAuthor" size="md" />

            <span>发布于 {{ formatTime(page.post.createTime) }}</span>
            <span v-if="Number(page.post.editCount || 0) > 0" :title="page.post.updateTime ? formatTime(page.post.updateTime) : ''">· 已编辑</span>
          </div>
        </header>

        <div class="post-article-body">
          <PostBlockRenderer :blocks="page.post.blocks" />
        </div>

        <footer class="post-article-foot">
          <div class="post-article-stats">
            <UiButton
              variant="ghost"
              class="post-article-like"
              :class="{ 'post-article-like--active': page.post.liked }"
              :aria-label="page.post.liked ? '取消点赞' : '点赞'"
              :disabled="postActions.loading"
              @click="postActions.toggleLike"
            >
              <ArrowUp :size="14" aria-hidden="true" />
              <span>{{ page.post.likeCount || 0 }} 赞</span>
            </UiButton>
            <span class="post-article-stat">{{ page.post.commentCount || 0 }} 回复</span>
          </div>

          <PostDetailActions
            :authed="page.authed"
            :post="page.post"
            :actions="postActions"
          />
        </footer>
      </article>

      <PostDetailComments
        :post="page.post"
        :discussion="discussion"
        :comment-editing="postActions.commentEditing"
      />

      <PostDetailComposer :authed="page.authed" :composer="discussion.composer" :go-login="page.goLogin" />
    </template>
  </div>
</template>

<script setup>
import { ArrowLeft, ArrowUp } from 'lucide-vue-next'
import UiUserCard from '../components/ui/UiUserCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import PostBlockRenderer from '../components/posts/PostBlockRenderer.vue'
import PostDetailActions from './post-detail/PostDetailActions.vue'
import PostDetailComments from './post-detail/PostDetailComments.vue'
import PostDetailComposer from './post-detail/PostDetailComposer.vue'
import { formatTime } from '../utils/time'
import { usePostDetailLoader } from './post-detail/usePostDetailLoader'

const { page, postActions, discussion } = usePostDetailLoader()
</script>

<style scoped src="./post-detail/PostDetailView.css"></style>
