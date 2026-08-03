<template>
  <div class="page profile-page">
    <UiBreadcrumb />

    <UiCard v-if="error">
      <UiState variant="error">{{ error }}</UiState>
    </UiCard>

    <UiCard v-else-if="loading">
      <div class="muted">加载中…</div>
    </UiCard>

    <UiCard v-else class="profile-card">
      <div class="profile-header">
        <div class="profile-avatar-wrapper">
          <UiAvatar :src="profile?.headerUrl || ''" :name="profileName" :title="profileName" :size="88" />
        </div>

        <div class="profile-info">
          <div class="profile-info-kicker">
            <span class="profile-info-kicker-label">公开身份</span>
            <span class="profile-info-kicker-meta">
              用户 ID
              <span class="profile-id-value profile-text-wrap" :title="String(profile?.id || userId)">
                {{ shortUserId(profile?.id || userId) }}
              </span>
            </span>
          </div>
          <div class="profile-name-row">
            <h1 class="profile-name profile-text-wrap" :title="profileName">{{ profileName }}</h1>
            <UiRoleBadge :user="profile" size="md" />
            <span v-if="showUserLevel" class="profile-chip" title="用户等级（基于签到）">用户等级 LV {{ Number(profile?.userLevel ?? 0) }}</span>
            <span v-if="showUserLevel" class="profile-chip" title="最近签到天数">最近签到 {{ Number(profile?.signInDaysInWindow ?? 0) }} 天</span>
            <span class="profile-chip" title="钱包资产">{{ walletAsset.chipText }}</span>
          </div>
          <div class="profile-meta muted">加入 {{ joinedYear || '—' }} · {{ followStatusText }}</div>
          <div class="profile-cta-row">
            <RouterLink class="btn secondary" :to="{ name: 'wallet' }">查看钱包</RouterLink>
            <RouterLink class="btn ghost" :to="{ name: 'followees', params: { userId } }">查看关注</RouterLink>
            <RouterLink class="btn ghost" :to="{ name: 'followers', params: { userId } }">查看粉丝</RouterLink>
          </div>
        </div>

        <div class="profile-actions">
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
          <template v-if="authed && meUserId && !isSelfProfile">
            <UiButton
              v-if="followStatus === false && followStatusState === 'ready'"
              @click="doFollow(true)"
              :disabled="actionLoading"
              class="primary"
            >
              关注
            </UiButton>
            <UiButton
              variant="secondary"
              v-else-if="followStatus === true && followStatusState === 'ready'"
              @click="doFollow(false)"
              :disabled="actionLoading"
            >
              取消关注
            </UiButton>
            <UiButton variant="secondary" v-else-if="followStatusState === 'error'" disabled>暂不可用</UiButton>
            <UiButton variant="secondary" v-else disabled>查询中…</UiButton>
            <UiButton
              :variant="isBlocked ? 'dangerSecondary' : 'secondary'"
              :disabled="actionLoading"
              @click="toggleBlock"
              class="profile-action-btn"
            >
              {{ isBlocked ? '已屏蔽' : '屏蔽' }}
            </UiButton>
            <UiButton variant="secondary" :disabled="actionLoading" @click="reportOpen = true">举报</UiButton>
            <RouterLink class="btn-icon profile-message-link" :to="privateMessageTo" title="发私信" aria-label="发私信">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
            </RouterLink>
          </template>
          <template v-if="authed && isSelfProfile">
            <RouterLink class="btn secondary" to="/settings">编辑资料</RouterLink>
          </template>
        </div>
      </div>

      <div class="profile-body">
        <div class="profile-stats-bar">
          <div class="profile-stat">
            <span class="profile-stat-val">{{ profile?.likeCount || 0 }}</span>
            <span class="profile-stat-label">获赞</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-val">{{ profile?.followeeCount || 0 }}</span>
            <span class="profile-stat-label">关注</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-val">{{ profile?.followerCount || 0 }}</span>
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
                <div class="profile-summary-value">{{ joinedYear || '—' }}</div>
                <div class="profile-summary-text">公开资料只展示可确认的成员年份和基础关系状态。</div>
              </div>
              <div class="profile-summary-card">
                <div class="profile-summary-label">钱包资产</div>
                <div class="profile-summary-value">{{ walletAsset.valueText }}</div>
                <div class="profile-summary-text">{{ walletAsset.description }}</div>
              </div>
              <div v-if="showUserLevel" class="profile-summary-card">
                <div class="profile-summary-label">签到用户等级</div>
                <div class="profile-summary-value">LV {{ Number(profile?.userLevel ?? 0) }}</div>
                <div class="profile-summary-text">最近签到 {{ Number(profile?.signInDaysInWindow ?? 0) }} 天，由成长任务规则实时计算。</div>
              </div>
              <div class="profile-summary-card">
                <div class="profile-summary-label">社交状态</div>
                <div class="profile-summary-value">{{ followStatusText }}</div>
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
            <div v-if="profileTimeline.length > 0" class="profile-post-feed">
              <RouterLink
                v-for="item in profileTimeline"
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
                v-for="step in communityNextSteps"
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
      v-if="reportOpen"
      target-type="user"
      :target-id="userId"
      @close="reportOpen = false"
      @submitted="reportOpen = false"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'
import { getUserProfile, listUserRecentComments, listUserRecentPosts } from '../api/services/userService'
import { followUser, unfollowUser, getFollowStatus } from '../api/services/socialService'
import { blockUser, unblockUser } from '../api/services/blockService'
import { showToast } from '../ui/toastService'
import { formatTime, formatTimeAgo } from '../utils/time'
import UiCard from '../components/ui/UiCard.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiState from '../components/ui/UiState.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'
import { buildCommunityNextSteps, buildProfileWalletAsset, describeFollowStatusText } from './userProfileSurface'
import { buildCanonicalConversationId } from './conversationDetailState'
import { buildProfileTimeline, collectTimelineUserIds } from './userProfileTimeline'

const emit = defineEmits(['trace'])
const props = defineProps({
  userId: { type: String, default: '' }
})

const auth = useAuthStore()
const authed = computed(() => !!auth.accessToken)
const taxonomy = useTaxonomyStore()
const postMetaCache = usePostMetaCacheStore()

const userId = computed(() => normalizeOpaqueId(props.userId))

const profile = ref(null)
const recentPosts = ref([])
const recentComments = ref([])
const timelineUsers = ref({})
const loading = ref(false)
const error = ref('')

const meUserId = computed(() => normalizeOpaqueId(auth.userId))
const actionLoading = ref(false)
const followStatus = ref(null)
const followStatusState = ref('idle')
const reportOpen = ref(false)
let reloadGeneration = 0
let actionGeneration = 0

const prefs = useSocialPrefsStore()
const isBlocked = computed(() => prefs.blockedSet.has(userId.value))
const authScope = computed(() => [
  auth.tokenGeneration,
  meUserId.value,
  authed.value ? 'authenticated' : 'anonymous'
].join(':'))
const viewScope = computed(() => `${userId.value}:${authScope.value}`)

const joinedYear = computed(() => {
  const ts = profile.value?.createTime
  if (!ts) return ''
  const d = new Date(ts)
  const y = d.getFullYear()
  return Number.isFinite(y) ? String(y) : ''
})
const profileName = computed(() => profile.value?.username || `成员 ${profile.value?.id || userId.value || ''}`)
const isSelfProfile = computed(() => sameOpaqueId(meUserId.value, userId.value))
const privateMessageTo = computed(() => {
  const conversationId = buildCanonicalConversationId(meUserId.value, userId.value)
  return conversationId ? `/messages/${conversationId}` : '/messages'
})
const showUserLevel = computed(() => profile.value?.showUserLevel === true)
const walletAsset = computed(() =>
  buildProfileWalletAsset({
    profile: profile.value,
    authed: authed.value,
    isSelf: isSelfProfile.value
  })
)
const followStatusText = computed(() =>
  describeFollowStatusText({
    followStatus: followStatus.value,
    followStatusState: followStatusState.value,
    authed: authed.value,
    isSelf: isSelfProfile.value
  })
)

const communityNextSteps = computed(() =>
  buildCommunityNextSteps({
    authed: authed.value,
    isSelf: isSelfProfile.value,
    userId: userId.value
  })
)

function shortUserId(value) {
  const raw = String(value || '')
  if (raw.length <= 12) return raw || '—'
  return `${raw.slice(0, 8)}...${raw.slice(-4)}`
}

const profileTimeline = computed(() =>
  buildProfileTimeline({
    posts: recentPosts.value,
    comments: recentComments.value,
    usersById: timelineUsers.value,
    limit: 6
  })
)

function categoryLabel(id) {
  const cid = normalizeOpaqueId(id)
  if (!cid) return ''
  const category = taxonomy.categoriesById.get(cid)
  return category?.name || `分类#${cid}`
}

function settle(promise) {
  return Promise.resolve(promise).then(
    (value) => ({ value, error: null }),
    (requestError) => ({ value: null, error: requestError })
  )
}

function reloadIsCurrent(generation, scope) {
  return generation === reloadGeneration && scope === viewScope.value
}

function clearViewState() {
  profile.value = null
  recentPosts.value = []
  recentComments.value = []
  timelineUsers.value = {}
  followStatus.value = null
  followStatusState.value = 'idle'
  loading.value = false
  error.value = ''
  reportOpen.value = false
}

async function reload() {
  const targetId = userId.value
  if (!targetId) {
    reloadGeneration += 1
    clearViewState()
    return
  }

  const scope = viewScope.value
  const generation = ++reloadGeneration
  const shouldLoadFollowStatus = authed.value
    && Boolean(meUserId.value)
    && !sameOpaqueId(meUserId.value, targetId)

  error.value = ''
  loading.value = true
  followStatus.value = null
  followStatusState.value = shouldLoadFollowStatus ? 'loading' : 'idle'

  const profileRequest = settle(getUserProfile(targetId, { force: true }))
  const recentPostsRequest = settle(listUserRecentPosts(targetId, { page: 0, size: 3 }))
  const recentCommentsRequest = settle(listUserRecentComments(targetId, { page: 0, size: 3 }))
  const followStatusRequest = shouldLoadFollowStatus
    ? settle(getFollowStatus(3, targetId, { force: true }))
    : Promise.resolve({ value: null, error: null })

  try {
    const [profileResult, postsResult, commentsResult, nextFollowResult] = await Promise.all([
      profileRequest,
      recentPostsRequest,
      recentCommentsRequest,
      followStatusRequest
    ])
    if (!reloadIsCurrent(generation, scope)) return
    if (profileResult.error) throw profileResult.error

    const nextPosts = postsResult.error || !Array.isArray(postsResult.value?.data)
      ? []
      : postsResult.value.data
    const nextComments = commentsResult.error || !Array.isArray(commentsResult.value?.data)
      ? []
      : commentsResult.value.data
    const timelineUserIds = collectTimelineUserIds({ posts: nextPosts, comments: nextComments })
    let nextTimelineUsers = {}
    if (timelineUserIds.length > 0) {
      try {
        nextTimelineUsers = await postMetaCache.ensureUserSummaries(timelineUserIds)
      } catch {
        nextTimelineUsers = {}
      }
    }
    if (!reloadIsCurrent(generation, scope)) return

    profile.value = profileResult.value
    recentPosts.value = nextPosts
    recentComments.value = nextComments
    timelineUsers.value = nextTimelineUsers
    if (shouldLoadFollowStatus) {
      const nextFollowStatus = nextFollowResult.error ? null : nextFollowResult.value?.data
      followStatus.value = typeof nextFollowStatus === 'boolean' ? nextFollowStatus : null
      followStatusState.value = typeof nextFollowStatus === 'boolean' ? 'ready' : 'error'
    } else {
      followStatus.value = null
      followStatusState.value = 'idle'
    }

    emit('trace', profileResult.value?._traceId || '')
    if (!postsResult.error) emit('trace', postsResult.value?.traceId || '')
    if (!commentsResult.error) emit('trace', commentsResult.value?.traceId || '')
    if (shouldLoadFollowStatus && !nextFollowResult.error) {
      emit('trace', nextFollowResult.value?.traceId || '')
    }
  } catch (e) {
    if (!reloadIsCurrent(generation, scope)) return
    error.value = e?.message || '加载失败'
  } finally {
    if (reloadIsCurrent(generation, scope)) loading.value = false
  }
}

function beginAction() {
  const targetId = userId.value
  if (
    actionLoading.value
    || !targetId
    || !authed.value
    || !meUserId.value
    || sameOpaqueId(meUserId.value, targetId)
  ) {
    return null
  }

  const action = {
    generation: ++actionGeneration,
    targetId,
    authScope: authScope.value,
    viewScope: viewScope.value
  }
  actionLoading.value = true
  return action
}

function actionIsCurrent(action) {
  return action.generation === actionGeneration && action.viewScope === viewScope.value
}

function finishAction(action) {
  if (action.generation === actionGeneration) actionLoading.value = false
}

async function doFollow(follow) {
  const action = beginAction()
  if (!action) return
  try {
    const resp = follow
      ? await followUser(3, action.targetId)
      : await unfollowUser(3, action.targetId)
    if (!actionIsCurrent(action)) return
    emit('trace', resp?.traceId || '')
    await reload()
  } catch (e) {
    if (!actionIsCurrent(action)) return
    error.value = e?.message || '关注操作失败'
  } finally {
    finishAction(action)
  }
}

onMounted(reload)
watch(viewScope, () => {
  reloadGeneration += 1
  actionGeneration += 1
  actionLoading.value = false
  clearViewState()
  if (userId.value) reload()
})
onMounted(() => {
  taxonomy.ensureCategories()
})

watch(
  authScope,
  () => {
    if (authed.value) {
      Promise.resolve(prefs.ensureBlocked(true)).catch(() => {})
    } else {
      prefs.clear()
    }
  },
  { immediate: true }
)

async function toggleBlock() {
  const action = beginAction()
  if (!action) return
  const wasBlocked = prefs.blockedSet.has(action.targetId)
  try {
    if (wasBlocked) {
      await unblockUser(action.targetId)
    } else {
      await blockUser(action.targetId)
    }
    if (action.authScope === authScope.value) {
      await prefs.ensureBlocked(true)
    }
    if (!actionIsCurrent(action)) return
    showToast({ type: 'success', text: wasBlocked ? '已解除屏蔽' : '已屏蔽该用户' })
  } catch (e) {
    if (!actionIsCurrent(action)) return
    error.value = e?.message || '操作失败'
  } finally {
    finishAction(action)
  }
}

onBeforeUnmount(() => {
  reloadGeneration += 1
  actionGeneration += 1
})
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
