<template>
  <div class="page posts-page">
    <UiPageHeader>
      <template #title>社区讨论</template>
      <template #subtitle>查看最新问题、未读回复和成员正在推进的话题。</template>
    </UiPageHeader>

    <div class="posts-workspace">
      <main class="posts-main-feed">
        <FeedToolbar
          :category-id="categoryId"
          :tag="tag"
          :categories="categories"
          :show-clear="showClear"
          :disabled="loading"
          @update:categoryId="setCategoryId"
          @clearTag="clearTag"
          @refresh="reload"
          @clear="clearQuery"
        />

        <UiTabs
          :model-value="order"
          :tabs="orderTabs"
          label="帖子排序"
          class="posts-order-tabs"
          @update:modelValue="setOrder"
        >
          <template #panel="{ active }">
            <div v-if="active" class="posts-feed-region">
              <div class="posts-context-strip">
                <span class="posts-context-item"><strong>{{ items.length || 0 }}</strong> 条当前讨论</span>
                <span class="posts-context-sep" aria-hidden="true">/</span>
                <span class="posts-context-item"><strong>{{ categories.length }}</strong> 个公开分类</span>
                <template v-if="newSinceLastSeenCount > 0">
                  <span class="posts-context-sep" aria-hidden="true">/</span>
                  <span class="posts-context-item posts-context-item--accent"><strong>{{ newSinceLastSeenCount }}</strong> 条新增未读</span>
                </template>
              </div>

              <UiButton
                v-if="authed && !isPublishFocused"
                variant="secondary"
                class="posts-feed-compose-strip"
                @click="isPublishFocused = true"
              >
                <span class="posts-feed-compose-leading">
                  <UiAvatar :src="me?.headerUrl" :name="me?.username || ''" :size="30" />
                  <span class="posts-feed-compose-copy">
                    <span class="posts-feed-compose-title">开始一个讨论</span>
                    <span class="posts-feed-compose-sub">把问题、经验或交易提醒发到社区时间线。</span>
                  </span>
                </span>
                <span class="posts-feed-compose-action">开始</span>
              </UiButton>

              <UiCard v-if="authed && isPublishFocused" class="posts-composer">
                <div class="posts-composer-editor">
                  <div class="posts-composer-head">
                    <div class="posts-composer-title">开始一个讨论</div>
                    <UiIconButton
                      class="posts-composer-close"
                      aria-label="关闭发帖编辑器"
                      @click="closeComposer"
                    >
                      ×
                    </UiIconButton>
                  </div>

                  <UiField label="标题" class="posts-composer-title-field">
                    <UiInput
                      v-model.trim="newTitle"
                      name="post-title"
                      placeholder="标题"
                      autocomplete="off"
                      :disabled="creating"
                    />
                  </UiField>

                  <PostBlockEditor
                    v-model="newBlocks"
                    class="posts-composer-block-editor"
                    :disabled="creating"
                  />

                  <div class="posts-composer-meta">
                    <UiField label="分类（可选）" class="posts-composer-field posts-composer-field--category">
                      <template #default="{ controlId }">
                        <select
                          :id="controlId"
                          v-model="newCategoryId"
                          name="post-category"
                          class="posts-composer-category-select"
                          :disabled="creating"
                        >
                          <option
                            v-for="option in composerCategoryOptions"
                            :key="String(option.value)"
                            :value="option.value"
                            :disabled="option.disabled"
                          >
                            {{ option.label }}
                          </option>
                        </select>
                      </template>
                    </UiField>

                    <UiField
                      label="标签"
                      help="回车/逗号添加，最多 5 个"
                      :error="newTagError"
                      class="posts-composer-field posts-composer-field--tags"
                    >
                      <template #default="{ controlId }">
                        <UiAutosuggestInput
                          :id="controlId"
                          v-model.trim="newTagDraft"
                          name="post-tag-draft"
                          placeholder="例如：Java（输入后回车确认）"
                          autocomplete="off"
                          :disabled="creating"
                          :suggestions="composerTagSuggestNames"
                          :commit-on-enter="true"
                          :commit-on-blur="true"
                          @commit="commitNewTags"
                          @keydown="onTagDraftKeydown"
                        />
                      </template>
                    </UiField>
                  </div>

                  <div v-if="newTags.length > 0" class="posts-composer-tags">
                    <button
                      v-for="t in newTags"
                      :key="t"
                      type="button"
                      class="posts-composer-tag"
                      :title="`移除标签 ${t}`"
                      :disabled="creating"
                      @click="removeNewTag(t)"
                    >
                      <span class="posts-composer-tag-text">#{{ t }}</span>
                      <X :size="12" aria-hidden="true" />
                    </button>
                  </div>

                  <div class="posts-composer-actions">
                    <div class="error posts-composer-submit-error">{{ createError }}</div>
                    <div class="posts-composer-action-group">
                      <UiButton @click="createPost" :disabled="creating" class="posts-composer-submit">
                        {{ creating ? '发布中' : '发布' }}
                      </UiButton>
                    </div>
                  </div>
                </div>
              </UiCard>

              <div v-if="loading && items.length === 0" class="posts-skeletons">
                <UiSkeleton v-for="i in 3" :key="i" variant="card" />
              </div>

              <UiState v-if="error && items.length === 0" variant="error" :title="error">
                <template #description>帖子流加载失败，可以重试或稍后再来。</template>
                <template #actions>
                  <UiButton variant="secondary" :disabled="loading" @click="reload">重试</UiButton>
                </template>
              </UiState>
              <div v-else-if="error" class="error posts-inline-error">{{ error }}</div>

              <div v-if="blockedHiddenCount > 0" class="posts-muted-note">
                已隐藏 {{ blockedHiddenCount }} 条来自已屏蔽用户的帖子
              </div>

              <UiState v-if="!loading && items.length === 0 && !error">
                当前视图暂无讨论
                <template #description>
                  可以重置筛选、查看热门，或者直接开始一个讨论。
                </template>
                <template #actions>
                  <UiButton variant="secondary" :disabled="loading" @click="reload">刷新时间线</UiButton>
                  <UiButton v-if="!authed" variant="ghost" @click="goLogin">登录</UiButton>
                  <UiButton v-else variant="ghost" @click="isPublishFocused = true">开始讨论</UiButton>
                </template>
              </UiState>

              <div v-if="shouldShowNewHint" class="posts-new-hint">
                <div class="posts-new-hint-left">
                  自上次访问后新增 <span class="posts-new-hint-num">{{ newSinceLastSeenCount }}</span> 条
                </div>
                <div class="posts-new-hint-actions">
                  <UiButton variant="secondary" :disabled="!canJumpToLastSeen" @click="scrollToLastSeenDivider">上次位置</UiButton>
                  <UiButton variant="ghost" @click="newHintDismissed = true">收起</UiButton>
                </div>
              </div>

              <div v-if="items.length > 0" class="posts-feed-list">
                <template v-for="(p, idx) in items" :key="p.id">
                  <div v-if="shouldShowLastSeenDivider && idx === newDividerIndex" ref="lastSeenDividerRef" class="posts-last-seen-divider">
                    上次看到这里
                  </div>

                  <article
                    class="posts-card"
                    :class="{ 'posts-card--unread': isUnread(p) }"
                    role="link"
                    tabindex="0"
                    @keydown.enter="onCardEnter($event, p)"
                    @click="openPost(p)"
                  >
                    <div class="posts-card-head">
                      <div class="posts-card-taxonomy">
                        <span v-if="isUnread(p)" class="posts-card-unread">未读</span>
                        <button
                          v-if="p.categoryId"
                          type="button"
                          class="posts-card-category"
                          :title="`查看分类 ${categoryLabel(p.categoryId)}`"
                          @click.stop="setCategoryId(p.categoryId)"
                        >
                          {{ categoryLabel(p.categoryId) }}
                        </button>
                        <button
                          v-for="t in (Array.isArray(p.tags) ? p.tags : [])"
                          :key="t"
                          type="button"
                          class="posts-card-tag"
                          :title="`查看标签 ${t}`"
                          @click.stop="setTag(t)"
                        >
                          #{{ t }}
                        </button>
                      </div>
                      <div class="posts-card-badges" v-if="p.type === 1 || p.status >= 1">
                        <UiBadge v-if="p.type === 1" variant="accent">置顶</UiBadge>
                        <UiBadge v-if="p.status === 1" variant="success">精华</UiBadge>
                      </div>
                    </div>

                    <h2 class="posts-card-title">{{ p.title }}</h2>

                    <div class="posts-card-snippet" v-if="p.preview">
                      {{ p.preview?.slice(0, 140) }}{{ (p.preview?.length || 0) > 140 ? '...' : '' }}
                    </div>

                    <div class="posts-card-meta">
                      <button
                        type="button"
                        class="posts-card-author"
                        @click.stop="openUserProfile(p.userId)"
                      >
                        <UiAvatar :src="p.author?.headerUrl || ''" :name="p.author?.username || ''" :size="18" />
                        <span class="posts-card-author-name">
                          {{ p.author?.username || `成员 ${p.userId}` }}
                        </span>
                      </button>
                      <span aria-hidden="true">·</span>
                      <span :title="formatTime(p.createTime)">发布 {{ formatTimeAgo(p.createTime) }}</span>
                    </div>

                    <div class="posts-card-foot">
                      <div class="posts-card-stats">
                        <span class="posts-card-stat">{{ Number(p.commentCount || 0) }} 回复</span>
                        <UiButton
                          variant="ghost"
                          class="posts-card-like"
                          :class="{ 'posts-card-like--active': p.liked }"
                          :aria-label="p.liked ? '取消点赞' : '点赞'"
                          @click.stop="togglePostLike(p)"
                        >
                          <ArrowUp :size="14" aria-hidden="true" />
                          <span>{{ p.likeCount || 0 }} 赞</span>
                        </UiButton>
                      </div>

                      <div class="posts-card-activity">
                        <template v-if="activityUserId(p)">
                          <button
                            type="button"
                            class="posts-card-activity-user"
                            :aria-label="`查看用户 ${activityUser(p)?.username || `成员 ${activityUserId(p)}`}`"
                            @click.stop="openUserProfile(activityUserId(p))"
                          >
                            <UiAvatar
                              :src="activityUser(p)?.headerUrl || ''"
                              :name="activityUser(p)?.username || ''"
                              :size="18"
                            />
                            <span>{{ activityUser(p)?.username || `成员 ${activityUserId(p)}` }}</span>
                          </button>
                          <span class="posts-card-activity-time">{{ formatTimeAgo(activityTime(p)) }}</span>
                        </template>
                        <span v-else class="posts-card-activity-time" :title="formatTime(activityTime(p))">
                          {{ formatTimeAgo(activityTime(p)) }}
                        </span>
                      </div>
                    </div>
                  </article>
                </template>
              </div>

              <div class="posts-load-more" v-if="hasNext || loading">
                <UiButton v-if="loading" variant="ghost" disabled>
                  <LoaderCircle :size="14" aria-hidden="true" class="posts-load-more-spinner" />
                  正在加载…
                </UiButton>
                <UiButton v-else variant="secondary" @click="loadMore" class="posts-load-more-btn">加载更多</UiButton>
              </div>
              <div v-if="!hasNext && items.length > 0" class="posts-end-note">
                没有更多内容了
              </div>
            </div>
          </template>
        </UiTabs>
      </main>

      <aside class="posts-context-panel" aria-label="社区上下文">
        <div class="posts-context-block">
          <strong>当前视图</strong>
          <span>{{ items.length || 0 }} 条讨论 · {{ categories.length }} 个分类</span>
        </div>
        <div class="posts-context-block">
          <strong>快速入口</strong>
          <span>使用分类筛选快速定位讨论。</span>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ArrowUp, LoaderCircle, X } from 'lucide-vue-next'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiIconButton from '../components/ui/UiIconButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import FeedToolbar from '../components/posts/FeedToolbar.vue'
import PostBlockEditor from '../components/posts/PostBlockEditor.vue'
import { formatTime, formatTimeAgo } from '../utils/time'
import { usePostsFeed } from './posts/usePostsFeed'

const orderTabs = [
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '最热' }
]

const {
  session,
  scope,
  feed,
  unread,
  composer
} = usePostsFeed()
const {
  authed,
  me,
  goLogin
} = session
const {
  categoryId,
  order,
  tag,
  categories,
  categoryLabel,
  showClear,
  setOrder,
  setCategoryId,
  setTag,
  clearTag,
  clearQuery
} = scope
const {
  items,
  hasNext,
  loading,
  error,
  blockedHiddenCount,
  activityTime,
  activityUserId,
  activityUser,
  openUserProfile,
  openPost,
  loadMore,
  reload,
  togglePostLike
} = feed
const {
  lastSeenDividerRef,
  newHintDismissed,
  newSinceLastSeenCount,
  newDividerIndex,
  shouldShowLastSeenDivider,
  shouldShowNewHint,
  canJumpToLastSeen,
  scrollToLastSeenDivider,
  isUnread
} = unread
const {
  isPublishFocused,
  newTitle,
  newBlocks,
  newCategoryId,
  composerCategoryOptions,
  newTagDraft,
  newTags,
  newTagError,
  composerTagSuggestNames,
  creating,
  createError,
  closeComposer,
  commitNewTags,
  onTagDraftKeydown,
  removeNewTag,
  createPost
} = composer

// 键盘打开只响应卡片自身获得焦点时的 Enter；嵌套按钮（点赞/作者/分类/标签）
// 的 Enter 走原生 click，不重复触发打开。
function onCardEnter(event, post) {
  if (event?.target !== event?.currentTarget) return
  openPost(post)
}
</script>

<style scoped src="./posts/PostsView.css"></style>
