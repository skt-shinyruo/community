<template>
  <div class="page posts-page">
    <UiPageHeader>
      <template #title>讨论</template>
      <template #subtitle>按排序、筛选、订阅和分类整理时间线。发帖入口保留在顶部，不把整个首屏变成编辑器。</template>
    </UiPageHeader>

    <section class="posts-toolbar-stage">
      <FeedToolbar
        :order="order"
        :filter="filter"
        :subscribed="subscribed"
        :show-subscribed-toggle="authed"
        :category-id="categoryId"
        :tag="tag"
        :categories="categories"
        :tag-suggestions="tagSuggestions"
        :order-options="orderOptions"
        :filter-options="filterOptions"
        :show-clear="showClear"
        :disabled="loading"
        @update:order="setOrder"
        @update:filter="setFilter"
        @update:subscribed="setSubscribed"
        @update:categoryId="setCategoryId"
        @update:tag="setTag"
        @refresh="reload"
        @clear="clearQuery"
      />

      <div class="posts-context-strip">
        <span class="posts-context-item"><strong>{{ items.length || 0 }}</strong> 条当前讨论</span>
        <span class="posts-context-sep" aria-hidden="true">/</span>
        <span class="posts-context-item"><strong>{{ categories.length }}</strong> 个公开分类</span>
        <template v-if="newSinceLastSeenCount > 0">
          <span class="posts-context-sep" aria-hidden="true">/</span>
          <span class="posts-context-item posts-context-item--accent"><strong>{{ newSinceLastSeenCount }}</strong> 条新增未读</span>
        </template>
      </div>
    </section>

    <div class="posts-feed">
      <UiButton
        v-if="authed && !isPublishFocused"
        variant="secondary"
        class="posts-feed-compose-strip"
        @click="isPublishFocused = true"
      >
        <span class="posts-feed-compose-leading">
          <UiAvatar :src="me?.headerUrl" :name="me?.username || ''" :size="30" />
          <span class="posts-feed-compose-copy">
            <span class="posts-feed-compose-title">发起讨论</span>
            <span class="posts-feed-compose-sub">把今天最值得展开的问题直接丢进时间线。</span>
          </span>
        </span>
        <span class="posts-feed-compose-action">开始</span>
      </UiButton>

      <UiCard v-if="authed && isPublishFocused" class="posts-composer">
        <div class="posts-composer-editor">
        <div class="posts-composer-head">
          <div class="posts-composer-title">发起讨论</div>
          <UiIconButton
            class="posts-composer-close"
            aria-label="关闭发帖编辑器"
            @click="isPublishFocused = false"
          >
            ×
          </UiIconButton>
        </div>
        <UiInput v-model.trim="newTitle" name="post-title" placeholder="标题" autocomplete="off" class="posts-composer-input" />
        <PostBlockEditor
          v-model="newBlocks"
          class="posts-composer-block-editor"
          :disabled="creating"
        />

        <div class="posts-composer-meta">
          <div class="posts-composer-field posts-composer-field--category">
            <div class="posts-composer-label">分类（可选）</div>
            <UiSelect
              v-model="newCategoryId"
              name="post-category"
              class="posts-composer-category-select"
              :disabled="creating"
              :options="composerCategoryOptions"
              placeholder="不选择"
            />
          </div>

          <div class="posts-composer-field posts-composer-field--tags">
            <div class="posts-composer-label">标签（回车/逗号添加，最多 5 个）</div>
            <UiAutosuggestInput
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
            <div v-if="newTagError" class="error posts-composer-error">{{ newTagError }}</div>

            <div v-if="newTags.length > 0" class="posts-composer-tags">
              <UiButton
                v-for="t in newTags"
                :key="t"
                variant="ghost"
                class="tag-btn"
                :title="`移除标签 ${t}`"
                @click="removeNewTag(t)"
              >
                <span class="tag">#{{ t }}</span>
                <span class="tag-btn-x" aria-hidden="true">×</span>
              </UiButton>
            </div>
          </div>
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
         <div v-for="i in 3" :key="i" class="posts-skeleton-card">
            <div class="posts-skeleton-meta">
              <div class="skeleton skeleton-text posts-skeleton-pill"></div>
              <div class="skeleton skeleton-text posts-skeleton-pill posts-skeleton-pill--sm"></div>
            </div>
            <div class="skeleton skeleton-text posts-skeleton-title"></div>
            <div class="skeleton skeleton-text posts-skeleton-line"></div>
            <div class="skeleton skeleton-text posts-skeleton-line posts-skeleton-line--short"></div>
            <div class="skeleton skeleton-text posts-skeleton-footer"></div>
         </div>
      </div>

      <UiEmpty v-if="error && items.length === 0" type="error">{{ error }}</UiEmpty>
      <div v-else-if="error" class="error">{{ error }}</div>

      <div v-if="blockedHiddenCount > 0" class="muted posts-muted-note">
        已隐藏 {{ blockedHiddenCount }} 条来自已屏蔽用户的帖子
      </div>

      <UiEmpty v-if="!loading && items.length === 0 && !error" class="posts-empty-inline">
        暂时还没有新的讨论进入这条时间线
        <template #description>
          试试切换到「热门」，或者直接发起一个问题。
          {{ authed ? '你现在就可以把今天最值得讨论的话题抛出来。' : '登录后就能把第一篇主帖送上时间线。' }}
        </template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新时间线</UiButton>
          <UiButton v-if="!authed" variant="ghost" @click="goLogin">登录</UiButton>
          <UiButton v-else variant="ghost" @click="isPublishFocused = true">发起讨论</UiButton>
        </template>
      </UiEmpty>

      <div v-if="shouldShowNewHint" class="topic-new-hint">
        <div class="topic-new-hint-left">
          自上次访问后新增 <span class="topic-new-hint-num">{{ newSinceLastSeenCount }}</span> 条
        </div>
        <div class="topic-new-hint-actions">
          <UiButton variant="secondary" class="topic-new-hint-btn" :disabled="!canJumpToLastSeen" @click="scrollToLastSeenDivider">上次位置</UiButton>
          <UiButton variant="ghost" class="topic-new-hint-btn" @click="newHintDismissed = true">收起</UiButton>
        </div>
      </div>
      
      <div v-if="items.length > 0" class="discussion-feed">
        <template v-for="(p, idx) in items" :key="p.id">
          <div v-if="shouldShowLastSeenDivider && idx === newDividerIndex" ref="lastSeenDividerRef" class="discussion-divider">
            上次看到这里
          </div>

          <article
            class="discussion-card"
            :class="{ 'is-unread': isUnread(p) }"
            role="link"
            tabindex="0"
            @keydown.enter="openPost(p)"
            @click="openPost(p)"
          >
            <div class="discussion-card-head">
              <div class="discussion-card-taxonomy">
                <span v-if="isUnread(p)" class="discussion-unread-pill" title="未读" aria-label="未读">未读</span>
                <span v-if="p.categoryId" class="discussion-category-chip">
                  {{ categoryLabel(p.categoryId) }}
                </span>
                <span v-for="t in (Array.isArray(p.tags) ? p.tags : [])" :key="t" class="discussion-tag-chip">
                  #{{ t }}
                </span>
              </div>
              <div class="discussion-card-badges" v-if="p.type === 1 || p.status >= 1">
                <UiBadge v-if="p.type === 1" variant="accent">置顶</UiBadge>
                <UiBadge v-if="p.status === 1" variant="success">精华</UiBadge>
              </div>
            </div>

            <h2 class="discussion-card-title">{{ p.title }}</h2>

            <div class="discussion-card-snippet" v-if="p.content">
              {{ p.content?.slice(0, 140) }}{{ (p.content?.length || 0) > 140 ? '...' : '' }}
            </div>

            <div class="discussion-card-meta">
              <div
                class="discussion-card-author"
                @click.stop="router.push({ name: 'userProfile', params: { userId: String(p.userId) } })"
              >
                <UiAvatar :src="p.author?.headerUrl || ''" :name="p.author?.username || ''" :size="18" />
                <span class="discussion-card-author-name">
                  {{ p.author?.username || `成员 ${p.userId}` }}
                </span>
              </div>
              <span>·</span>
              <span :title="formatTime(p.createTime)">发布 {{ formatTimeAgo(p.createTime) }}</span>
            </div>

            <div class="discussion-card-foot">
              <div class="discussion-card-stats">
                <span class="discussion-stat">{{ Number(p.commentCount || 0) }} 回复</span>
                <UiButton
                  variant="ghost"
                  class="discussion-like-btn"
                  :class="{ active: p.liked }"
                  :aria-label="p.liked ? '取消点赞' : '点赞'"
                  @click.stop="togglePostLike(p)"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <path d="M12 19V5M5 12l7-7 7 7" />
                  </svg>
                  <span>{{ p.likeCount || 0 }} 赞</span>
                </UiButton>
              </div>

              <div class="discussion-card-activity">
                <template v-if="activityUserId(p)">
                  <UiButton
                    variant="ghost"
                    class="discussion-activity-user"
                    :aria-label="`查看用户 ${activityUser(p)?.username || `成员 ${activityUserId(p)}`}`"
                    @click.stop="router.push({ name: 'userProfile', params: { userId: String(activityUserId(p)) } })"
                  >
                    <UiAvatar
                      :src="activityUser(p)?.headerUrl || ''"
                      :name="activityUser(p)?.username || ''"
                      :size="18"
                    />
                    <span>{{ activityUser(p)?.username || `成员 ${activityUserId(p)}` }}</span>
                  </UiButton>
                  <span class="discussion-activity-time">{{ formatTimeAgo(activityTime(p)) }}</span>
                </template>
                <span v-else class="discussion-activity-time" :title="formatTime(activityTime(p))">
                  {{ formatTimeAgo(activityTime(p)) }}
                </span>
              </div>
            </div>
          </article>
        </template>
      </div>
    </div>

    <!-- Load More -->
    <div class="posts-load-more" v-if="hasNext || loading">
      <UiButton v-if="loading" variant="ghost" disabled>加载中...</UiButton>
      <UiButton v-else variant="secondary" @click="loadMore" class="posts-load-more-btn">加载更多</UiButton>
    </div>
    <div v-if="!hasNext && items.length > 0" class="muted posts-end-note">
       没有更多内容了
    </div>
  </div>
</template>

<script setup>
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiIconButton from '../components/ui/UiIconButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import FeedToolbar from '../components/posts/FeedToolbar.vue'
import PostBlockEditor from '../components/posts/PostBlockEditor.vue'
import { usePostsFeed } from './posts/usePostsFeed'

const emit = defineEmits(['trace'])
const {
  auth,
  authed,
  me,
  router,
  order,
  filter,
  subscribed,
  categoryId,
  tag,
  orderOptions,
  filterOptions,
  taxonomy,
  categories,
  composerCategoryOptions,
  categoryLabel,
  showClear,
  blockedSet,
  blockedHiddenCount,
  goLogin,
  setOrder,
  setFilter,
  setCategoryId,
  setTag,
  setSubscribed,
  clearQuery,
  page,
  size,
  items,
  hasNext,
  loading,
  error,
  isPublishFocused,
  newTitle,
  newBlocks,
  newCategoryId,
  newTagDraft,
  newTags,
  newTagError,
  composerTagSuggest,
  composerTagSuggestNames,
  creating,
  createError,
  seenBaselineAt,
  toMs,
  activityTime,
  activityUserId,
  activityUser,
  commitNewTags,
  onTagDraftKeydown,
  removeNewTag,
  tagSuggestions,
  lastSeenDividerRef,
  newHintDismissed,
  isDefaultLatestFeed,
  newSinceLastSeenCount,
  newDividerIndex,
  shouldShowLastSeenDivider,
  shouldShowNewHint,
  canJumpToLastSeen,
  getLastSeenDividerEl,
  scrollToLastSeenDivider,
  isUnread,
  openPost,
  load,
  loadMore,
  reload,
  togglePostLike,
  createPost,
  formatTime,
  formatTimeAgo
} = usePostsFeed(emit)
</script>

<style src="./posts/PostsView.css"></style>
