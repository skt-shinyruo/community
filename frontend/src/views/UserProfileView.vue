<template>
  <div class="page profile-page">
    <UiBreadcrumb />

    <UiCard v-if="model.error">
      <UiState variant="error">{{ model.error }}</UiState>
    </UiCard>

    <UiCard v-else-if="model.loading">
      <div class="muted">加载中…</div>
    </UiCard>

    <UiCard v-else class="profile-card">
      <div class="profile-header">
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
          <div class="profile-meta muted">加入 {{ model.joinedYear || '—' }} · {{ model.followStatusText }}</div>
          <div class="profile-cta-row">
            <RouterLink class="btn secondary" :to="{ name: 'wallet' }">查看钱包</RouterLink>
            <RouterLink class="btn ghost" :to="{ name: 'followees', params: { userId: model.userId } }">查看关注</RouterLink>
            <RouterLink class="btn ghost" :to="{ name: 'followers', params: { userId: model.userId } }">查看粉丝</RouterLink>
          </div>
        </div>

        <div class="profile-actions">
          <UiButton variant="secondary" :disabled="model.loading" @click="actions.reload">刷新</UiButton>
          <template v-if="model.authed && model.meUserId && !model.isSelfProfile">
            <UiButton
              v-if="model.followStatus === false && model.followStatusState === 'ready'"
              @click="actions.follow"
              :disabled="model.actionLoading"
              class="primary"
            >
              关注
            </UiButton>
            <UiButton
              variant="secondary"
              v-else-if="model.followStatus === true && model.followStatusState === 'ready'"
              @click="actions.unfollow"
              :disabled="model.actionLoading"
            >
              取消关注
            </UiButton>
            <UiButton variant="secondary" v-else-if="model.followStatusState === 'error'" disabled>暂不可用</UiButton>
            <UiButton variant="secondary" v-else disabled>查询中…</UiButton>
            <UiButton
              :variant="model.isBlocked ? 'dangerSecondary' : 'secondary'"
              :disabled="model.actionLoading"
              @click="actions.toggleBlocked"
              class="profile-action-btn"
            >
              {{ model.isBlocked ? '已屏蔽' : '屏蔽' }}
            </UiButton>
            <UiButton variant="secondary" :disabled="model.actionLoading" @click="actions.openReport">举报</UiButton>
            <RouterLink class="btn-icon profile-message-link" :to="model.privateMessageTo" title="发私信" aria-label="发私信">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
            </RouterLink>
          </template>
          <template v-if="model.authed && model.isSelfProfile">
            <RouterLink class="btn secondary" to="/settings">编辑资料</RouterLink>
          </template>
        </div>
      </div>

      <div class="profile-body">
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

        <div class="profile-sections">
          <section class="profile-section">
            <div class="profile-section-head">
              <div>
                <div class="profile-section-eyebrow">Overview</div>
                <div class="profile-section-title">公开资料</div>
              </div>
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

          <section class="profile-section">
            <div class="profile-section-head">
              <div>
                <div class="profile-section-eyebrow">Recent Activity</div>
                <div class="profile-section-title">社区动向</div>
              </div>
              <div class="profile-section-note">基于当前可用的公开关系与统计</div>
            </div>
            <div v-if="model.profileTimeline.length > 0" class="profile-post-feed">
              <RouterLink
                v-for="item in model.profileTimeline"
                :key="item.key"
                class="profile-post-card"
                :to="item.route"
              >
                <div class="profile-post-taxonomy">
                  <span class="tag topic-category">{{ item.title }}</span>
                </div>
                <div class="profile-post-title">{{ item.headline }}</div>
                <div v-if="item.contextUser" class="profile-post-context">
                  <span class="profile-post-context-label">{{ item.contextLabel }}</span>
                  <UiAvatar :src="item.contextUser?.headerUrl || ''" :name="item.contextUser?.username || ''" :size="18" />
                  <span class="profile-post-context-name">{{ item.contextUser?.username || '社区成员' }}</span>
                </div>
                <div class="profile-post-body">{{ item.body }}</div>
                <div class="profile-post-meta">
                  <span :title="formatTime(item.timestamp)">
                    活跃于 {{ formatTimeAgo(item.timestamp) }}
                  </span>
                </div>
              </RouterLink>
            </div>
            <div v-else class="profile-empty-activity">
              <div class="profile-empty-title">暂无公开动态</div>
              <div class="profile-empty-text">这个成员近期没有公开帖子或评论，先显示当前可用的身份与关系状态。</div>
            </div>
            <div class="profile-next-steps">
              <RouterLink
                v-for="step in model.communityNextSteps"
                :key="step.key"
                class="btn"
                :class="step.variant"
                :to="step.to"
              >
                {{ step.label }}
              </RouterLink>
            </div>
          </section>
        </div>
      </div>
    </UiCard>

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
import { formatTime, formatTimeAgo } from '../utils/time'
import UiCard from '../components/ui/UiCard.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiState from '../components/ui/UiState.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import { useUserProfilePage } from './useUserProfilePage'

const emit = defineEmits(['trace'])
const props = defineProps({
  userId: { type: String, default: '' }
})

const { model, actions, lifecycle } = useUserProfilePage({
  userId: toRef(props, 'userId'),
  onTrace: (traceId) => emit('trace', traceId)
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

.profile-eyebrow,
.profile-info-kicker-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-3);
}

.profile-card {
  padding: 0;
  overflow: hidden;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  box-shadow: var(--shadow-sm);
}

.profile-header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: start;
  gap: 20px;
  padding: 24px;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 96%, var(--bg) 4%);
}

.profile-body {
  position: relative;
  padding-bottom: 24px;
}

.profile-avatar-wrapper {
  min-width: 0;
}

.profile-avatar-wrapper :deep(.avatar) {
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.profile-action-btn {
  min-width: 96px;
}

.profile-message-link {
  align-self: center;
}

.profile-info {
  min-width: 0;
}

.profile-info-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.profile-info-kicker-meta {
  min-width: 0;
  font-size: 12px;
  color: var(--text-3);
  letter-spacing: 0;
}

.profile-name-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
}

.profile-name {
  margin: 0;
  font-family: var(--font-sans);
  font-size: 32px;
  font-weight: 800;
  line-height: 1.12;
  min-width: 0;
  max-width: 100%;
}

.profile-chip {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 10%, var(--surface) 90%);
  color: var(--text-1);
  font-size: 12px;
  font-weight: 700;
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
  margin-top: 6px;
  font-size: 13px;
}

.profile-cta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.profile-stats-bar {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 0 24px;
  background: transparent;
}

.profile-stat {
  padding: 18px 16px;
  text-align: left;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: var(--surface);
}

.profile-stat-val {
  display: block;
  font-family: var(--font-sans);
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 800;
  color: var(--text-1);
  line-height: var(--line-tight);
}

.profile-stat-label {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-2);
}

.profile-degraded-note {
  padding: 0 24px;
  margin-top: 8px;
  font-size: 12px;
}

.profile-sections {
  display: grid;
  gap: 18px;
  padding: 20px 24px 24px;
}

.profile-section {
  display: grid;
  gap: 14px;
}

.profile-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.profile-section-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-3);
  margin-bottom: 4px;
}

.profile-section-title {
  font-family: var(--font-sans);
  font-size: 24px;
  font-weight: 800;
  color: var(--text-1);
}

.profile-section-note {
  color: var(--text-3);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.profile-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.profile-summary-card {
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  border-radius: 8px;
  padding: 18px;
  background: var(--surface);
  display: grid;
  gap: 8px;
  min-width: 0;
}

.profile-summary-label {
  font-size: 12px;
  color: var(--text-3);
}

.profile-summary-value {
  font-family: var(--font-sans);
  font-size: 26px;
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
  gap: 12px;
}

.profile-post-card {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: var(--surface);
  color: inherit;
  text-decoration: none;
}

.profile-post-card:hover {
  text-decoration: none;
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.profile-post-taxonomy,
.profile-post-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.profile-post-context {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-2);
}

.profile-post-context-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
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
  font-family: var(--font-sans);
  font-size: clamp(20px, 2.2vw, 24px);
  font-weight: 800;
  line-height: 1.25;
  color: var(--text-1);
}

.profile-post-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-2);
  overflow-wrap: anywhere;
  word-break: break-word;
}

.profile-post-meta {
  font-size: 12px;
  color: var(--text-3);
  letter-spacing: 0.04em;
}

.profile-next-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.profile-empty-activity {
  display: grid;
  gap: 6px;
  padding: 16px 18px;
  border: 1px dashed color-mix(in srgb, var(--border) 84%, transparent 16%);
  border-radius: 8px;
  background: color-mix(in srgb, var(--surface) 94%, var(--bg) 6%);
}

.profile-empty-title {
  font-size: 15px;
  font-weight: 800;
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
    gap: 14px;
    padding: 16px;
  }

  .profile-avatar-wrapper {
    padding-left: 0;
  }

  .profile-actions {
    justify-content: flex-start;
  }

  .profile-name {
    font-size: 28px;
  }

  .profile-stats-bar {
    grid-template-columns: 1fr;
    padding: 0 16px;
  }

  .profile-degraded-note {
    padding: 0 16px;
  }

  .profile-sections {
    padding: 16px;
  }

  .profile-summary-grid {
    grid-template-columns: 1fr;
  }

  .profile-post-feed {
    grid-template-columns: 1fr;
  }
}
</style>
