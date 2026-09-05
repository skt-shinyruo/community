<template>
  <div class="page profile-page">
    <UiBreadcrumb />

    <UiState v-if="model.error" variant="error" :title="model.error">
      <template #description>个人主页加载失败，可以重试或稍后再来。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="model.loading" @click="actions.reload">重试</UiButton>
      </template>
    </UiState>

    <UiSkeleton v-else-if="model.loading" variant="detail" />

    <template v-else>
      <header class="profile-header">
        <div class="profile-avatar-wrapper">
          <UiAvatar :src="model.profile?.headerUrl || ''" :name="model.profileName" :title="model.profileName" :size="88" />
        </div>

        <div class="profile-info">
          <div class="profile-info-kicker">
            <span class="profile-info-kicker-label">公开身份</span>
            <span class="profile-info-kicker-meta">
              用户 ID
              <span class="profile-id-value profile-text-wrap" :title="String(model.profile?.id || model.userId)">
                {{ shortUserId(model.profile?.id || model.userId) }}
              </span>
            </span>
          </div>
          <div class="profile-name-row">
            <h1 class="profile-name profile-text-wrap" :title="model.profileName">{{ model.profileName }}</h1>
            <UiRoleBadge :user="model.profile" size="md" />
            <span v-if="model.showUserLevel" class="profile-chip" title="用户等级（基于签到）">用户等级 LV {{ Number(model.profile?.userLevel ?? 0) }}</span>
            <span v-if="model.showUserLevel" class="profile-chip" title="最近签到天数">最近签到 {{ Number(model.profile?.signInDaysInWindow ?? 0) }} 天</span>
            <span class="profile-chip" title="钱包资产">{{ model.walletAsset.chipText }}</span>
          </div>
          <div class="profile-meta">加入 {{ model.joinedYear || '—' }} · {{ model.followStatusText }}</div>
          <div class="profile-cta-row">
            <UiButton variant="secondary" :to="{ name: 'wallet' }">查看钱包</UiButton>
            <UiButton variant="ghost" :to="{ name: 'followees', params: { userId: model.userId } }">查看关注</UiButton>
            <UiButton variant="ghost" :to="{ name: 'followers', params: { userId: model.userId } }">查看粉丝</UiButton>
          </div>
        </div>

        <div class="profile-actions">
          <UiButton variant="secondary" :disabled="model.loading" @click="actions.reload">刷新</UiButton>
          <template v-if="model.authed && model.meUserId && !model.isSelfProfile">
            <UiButton
              v-if="model.followStatus === false && model.followStatusState === 'ready'"
              :disabled="model.actionLoading"
              @click="actions.follow"
            >
              关注
            </UiButton>
            <UiButton
              variant="secondary"
              v-else-if="model.followStatus === true && model.followStatusState === 'ready'"
              :disabled="model.actionLoading"
              @click="actions.unfollow"
            >
              取消关注
            </UiButton>
            <UiButton variant="secondary" v-else-if="model.followStatusState === 'error'" disabled>暂不可用</UiButton>
            <UiButton variant="secondary" v-else disabled>查询中…</UiButton>
            <UiButton variant="secondary" class="profile-message-link" :to="model.privateMessageTo">
              <MessageSquare :size="16" aria-hidden="true" />
              发私信
            </UiButton>
            <UiButton
              :variant="model.isBlocked ? 'dangerSecondary' : 'secondary'"
              :disabled="model.actionLoading"
              @click="actions.toggleBlocked"
              class="profile-action-btn"
            >
              {{ model.isBlocked ? '已屏蔽' : '屏蔽' }}
            </UiButton>
            <UiButton variant="secondary" :disabled="model.actionLoading" @click="actions.openReport">举报</UiButton>
          </template>
          <template v-if="model.authed && model.isSelfProfile">
            <UiButton variant="secondary" to="/settings">编辑资料</UiButton>
          </template>
        </div>
      </header>

      <div class="profile-stats-bar">
        <div class="profile-stat">
          <span class="profile-stat-val">{{ model.profile?.likeCount || 0 }}</span>
          <span class="profile-stat-label">获赞</span>
        </div>
        <div class="profile-stat">
          <span class="profile-stat-val">{{ model.profile?.followeeCount || 0 }}</span>
          <span class="profile-stat-label">关注</span>
        </div>
        <div class="profile-stat">
          <span class="profile-stat-val">{{ model.profile?.followerCount || 0 }}</span>
          <span class="profile-stat-label">粉丝</span>
        </div>
      </div>

      <section class="profile-section" aria-label="公开资料">
        <div class="profile-section-head">
          <h2 class="profile-section-title">公开资料</h2>
        </div>
        <div class="profile-summary-grid">
          <div class="profile-summary-card">
            <div class="profile-summary-label">加入时间</div>
            <div class="profile-summary-value">{{ model.joinedYear || '—' }}</div>
            <div class="profile-summary-text">公开资料只展示可确认的成员年份和基础关系状态。</div>
          </div>
          <div class="profile-summary-card">
            <div class="profile-summary-label">钱包资产</div>
            <div class="profile-summary-value">{{ model.walletAsset.valueText }}</div>
            <div class="profile-summary-text">{{ model.walletAsset.description }}</div>
          </div>
          <div v-if="model.showUserLevel" class="profile-summary-card">
            <div class="profile-summary-label">签到用户等级</div>
            <div class="profile-summary-value">LV {{ Number(model.profile?.userLevel ?? 0) }}</div>
            <div class="profile-summary-text">最近签到 {{ Number(model.profile?.signInDaysInWindow ?? 0) }} 天，由成长任务规则实时计算。</div>
          </div>
          <div class="profile-summary-card">
            <div class="profile-summary-label">社交状态</div>
            <div class="profile-summary-value">{{ model.followStatusText }}</div>
            <div class="profile-summary-text">如果你已登录，这里反映你与该用户当前的关注关系。</div>
          </div>
        </div>
      </section>

      <section class="profile-section" aria-label="社区动向">
        <div class="profile-section-head">
          <h2 class="profile-section-title">社区动向</h2>
          <div class="profile-section-note">基于当前可用的公开关系与统计</div>
        </div>
        <div v-if="model.profileTimeline.length > 0" class="profile-post-feed">
          <RouterLink
            v-for="item in model.profileTimeline"
            :key="item.key"
            class="profile-post-card"
            :to="item.route"
          >
            <div class="profile-post-head">
              <span class="profile-post-kind">{{ item.title }}</span>
              <span class="profile-post-time" :title="formatTime(item.timestamp)">
                活跃于 {{ formatTimeAgo(item.timestamp) }}
              </span>
            </div>
            <div class="profile-post-title">{{ item.headline }}</div>
            <div v-if="item.contextUser" class="profile-post-context">
              <span class="profile-post-context-label">{{ item.contextLabel }}</span>
              <UiAvatar :src="item.contextUser?.headerUrl || ''" :name="item.contextUser?.username || ''" :size="18" />
              <span class="profile-post-context-name">{{ item.contextUser?.username || '社区成员' }}</span>
            </div>
            <div class="profile-post-body">{{ item.body }}</div>
          </RouterLink>
        </div>
        <div v-else class="profile-empty-activity">
          <div class="profile-empty-title">暂无公开动态</div>
          <div class="profile-empty-text">这个成员近期没有公开帖子或评论，先显示当前可用的身份与关系状态。</div>
        </div>
        <div class="profile-next-steps">
          <UiButton
            v-for="step in model.communityNextSteps"
            :key="step.key"
            :variant="step.variant"
            :to="step.to"
          >
            {{ step.label }}
          </UiButton>
        </div>
      </section>
    </template>

    <ReportModal
      v-if="model.reportOpen"
      target-type="user"
      :target-id="model.userId"
      @close="actions.closeReport"
      @submitted="actions.closeReport"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, toRef } from 'vue'
import { MessageSquare } from 'lucide-vue-next'
import { formatTime, formatTimeAgo } from '../utils/time'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import { useUserProfilePage } from './useUserProfilePage'

const props = defineProps({
  userId: { type: String, default: '' }
})

const { model, actions, lifecycle } = useUserProfilePage({
  userId: toRef(props, 'userId')
})

function shortUserId(value) {
  const raw = String(value || '')
  if (raw.length <= 12) return raw || '—'
  return `${raw.slice(0, 8)}...${raw.slice(-4)}`
}

onMounted(lifecycle.mount)
onBeforeUnmount(lifecycle.unmount)
</script>

<style scoped>
.profile-page {
  max-width: 980px;
}

.profile-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-5);
  padding: var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.profile-avatar-wrapper {
  min-width: 0;
}

.profile-info {
  min-width: 0;
}

.profile-info-kicker {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-bottom: var(--space-2);
}

.profile-info-kicker-label {
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--text-3);
}

.profile-info-kicker-meta {
  min-width: 0;
  font-size: var(--text-xs);
  color: var(--text-3);
}

.profile-name-row {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
}

.profile-name {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  line-height: var(--line-tight);
  letter-spacing: 0;
  min-width: 0;
  max-width: 100%;
}

.profile-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--accent-weak);
  color: var(--accent-text);
  font-size: var(--text-xs);
  font-weight: 600;
}

.profile-text-wrap {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.profile-id-value {
  display: inline;
  color: var(--text-2);
}

.profile-meta {
  margin-top: var(--space-2);
  font-size: 13px;
  color: var(--text-3);
}

.profile-cta-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.profile-action-btn {
  min-width: 96px;
}

.profile-stats-bar {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.profile-stat {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
}

.profile-stat-val {
  display: block;
  font-size: 28px;
  font-weight: 800;
  color: var(--text-1);
  line-height: var(--line-tight);
}

.profile-stat-label {
  display: block;
  margin-top: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-2);
}

.profile-section {
  display: grid;
  gap: var(--space-3);
}

.profile-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.profile-section-title {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.profile-section-note {
  color: var(--text-3);
  font-size: var(--text-xs);
}

.profile-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.profile-summary-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  background: var(--surface);
  display: grid;
  gap: var(--space-2);
  min-width: 0;
  align-content: start;
}

.profile-summary-label {
  font-size: var(--text-xs);
  color: var(--text-3);
}

.profile-summary-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-1);
  line-height: 1.2;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.profile-summary-text {
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-2);
}

.profile-post-feed {
  display: grid;
  gap: var(--space-3);
}

.profile-post-card {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
  color: inherit;
  text-decoration: none;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.profile-post-card:hover {
  text-decoration: none;
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.profile-post-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.profile-post-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.profile-post-kind {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: var(--text-xs);
  font-weight: 500;
}

.profile-post-time {
  font-size: 13px;
  color: var(--text-3);
}

.profile-post-context {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  color: var(--text-2);
}

.profile-post-context-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-3);
}

.profile-post-context-name {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-1);
  overflow-wrap: anywhere;
  word-break: break-word;
}

.profile-post-title {
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
  overflow-wrap: anywhere;
  word-break: break-word;
}

.profile-post-body {
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--text-2);
  overflow-wrap: anywhere;
  word-break: break-word;
}

.profile-next-steps {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.profile-empty-activity {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-5);
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.profile-empty-title {
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--text-1);
}

.profile-empty-text {
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-2);
}

@media (max-width: 768px) {
  .profile-header {
    grid-template-columns: 1fr;
    gap: var(--space-3);
    padding: var(--space-4);
  }

  .profile-actions {
    justify-content: flex-start;
  }

  .profile-name {
    font-size: 24px;
  }

  .profile-stats-bar {
    grid-template-columns: 1fr;
  }

  .profile-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
