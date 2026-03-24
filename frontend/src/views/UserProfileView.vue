<template>
  <div class="page profile-page">
    <UiBreadcrumb />

    <UiCard v-if="error">
      <UiEmpty type="error">{{ error }}</UiEmpty>
    </UiCard>

    <UiCard v-else-if="loading">
      <div class="muted">加载中…</div>
    </UiCard>

    <UiCard v-else class="profile-card">
      <div class="profile-cover">
        <div class="profile-cover-sheet">
          <div class="profile-cover-kicker">Member Snapshot</div>
          <div class="profile-cover-title">{{ profile?.username || `成员 ${profile?.id}` }}</div>
          <div class="profile-cover-subtitle">关注关系、积分和公开信息会先汇总在这里，帮助你判断这个成员在社区里的存在感。</div>
        </div>
      </div>

      <div class="profile-body">
        <div class="profile-avatar-wrapper">
          <UiAvatar :src="profile?.headerUrl || ''" :name="profile?.username || ''" :size="120" />
        </div>

        <div class="profile-actions">
          <UiButton variant="secondary" :disabled="loading" @click="reload">刷新</UiButton>
          <template v-if="authed && meUserId && meUserId !== Number(userId)">
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
            <RouterLink class="btn-icon profile-message-link" :to="`/messages`" title="发私信" aria-label="发私信">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
            </RouterLink>
          </template>
          <template v-if="authed && meUserId === Number(userId)">
            <RouterLink class="btn secondary" to="/settings">编辑资料</RouterLink>
          </template>
        </div>

        <div class="profile-info">
          <div class="profile-info-kicker">
            <span class="profile-info-kicker-label">公开身份</span>
            <span class="profile-info-kicker-meta">用户 ID · {{ userId }}</span>
          </div>
          <div class="profile-name-row">
            <h1 class="profile-name">{{ profile?.username || `成员 ${profile?.id}` }}</h1>
            <UiRoleBadge :user="profile" size="md" />
            <span class="profile-chip" title="等级（基于积分）">LV {{ Number(profile?.level || 1) }}</span>
            <span class="profile-chip" title="积分">{{ Number(profile?.score || 0) }} 分</span>
          </div>
          <div class="profile-meta muted">用户 ID：{{ userId }} · 加入 {{ joinedYear || '—' }}</div>
          <div class="profile-cta-row">
            <RouterLink class="btn secondary" :to="{ name: 'leaderboard' }">查看排行榜</RouterLink>
            <RouterLink class="btn ghost" :to="{ name: 'followees', params: { userId } }">查看关注</RouterLink>
            <RouterLink class="btn ghost" :to="{ name: 'followers', params: { userId } }">查看粉丝</RouterLink>
          </div>
        </div>

        <div class="profile-stats-bar">
          <div class="profile-stat">
            <span class="profile-stat-val">{{ socialDegraded ? '—' : (profile?.likeCount || 0) }}</span>
            <span class="profile-stat-label">获赞</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-val">{{ socialDegraded ? '—' : (profile?.followeeCount || 0) }}</span>
            <span class="profile-stat-label">关注</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-val">{{ socialDegraded ? '—' : (profile?.followerCount || 0) }}</span>
            <span class="profile-stat-label">粉丝</span>
          </div>
        </div>
        <div v-if="socialDegraded" class="muted profile-degraded-note">
          统计暂不可用（下游服务降级），可稍后刷新。
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
                <div class="profile-summary-label">当前身份</div>
                <div class="profile-summary-value">{{ profile?.username || `成员 ${profile?.id}` }}</div>
                <div class="profile-summary-text">这是其他人进入你主页时看到的公开身份信息。</div>
              </div>
              <div class="profile-summary-card">
                <div class="profile-summary-label">社区影响力</div>
                <div class="profile-summary-value">{{ Number(profile?.score || 0) }} 分</div>
                <div class="profile-summary-text">积分与等级共同决定你在排行榜和讨论中的可见度。</div>
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
            <div class="profile-signal-grid">
              <article v-for="signal in communitySignals" :key="signal.key" class="profile-signal-card">
                <div class="profile-signal-label">{{ signal.label }}</div>
                <div class="profile-signal-value">{{ signal.value }}</div>
                <div class="profile-signal-text">{{ signal.text }}</div>
              </article>
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
      :target-id="Number(userId || 0)"
      @close="reportOpen = false"
      @submitted="reportOpen = false"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'
import { getUserProfile, listUserRecentComments, listUserRecentPosts } from '../api/services/userService'
import { followUser, unfollowUser, getFollowStatus } from '../api/services/socialService'
import { blockUser, unblockUser } from '../api/services/blockService'
import { formatTime, formatTimeAgo } from '../utils/time'
import UiCard from '../components/ui/UiCard.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiRoleBadge from '../components/ui/UiRoleBadge.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import { buildCommunityNextSteps, buildCommunitySignals, describeFollowStatusText } from './userProfileSurface'
import { buildProfileTimeline, collectTimelineUserIds } from './userProfileTimeline'

const emit = defineEmits(['trace'])
const props = defineProps({
  userId: { type: String, default: '' }
})

const auth = useAuthStore()
const authed = computed(() => !!auth.accessToken)
const taxonomy = useTaxonomyStore()
const postMetaCache = usePostMetaCacheStore()

const userId = computed(() => String(props.userId || ''))

const profile = ref(null)
const recentPosts = ref([])
const recentComments = ref([])
const timelineUsers = ref({})
const loading = ref(false)
const error = ref('')
const socialDegraded = computed(() => !!profile.value?.socialDegraded)

const meUserId = computed(() => Number(auth.userId || 0))
const actionLoading = ref(false)
const followStatus = ref(null)
const followStatusState = ref('idle')
const reportOpen = ref(false)

const prefs = useSocialPrefsStore()
const isBlocked = computed(() => prefs.blockedSet.has(Number(userId.value || 0)))

const joinedYear = computed(() => {
  const ts = profile.value?.createTime
  if (!ts) return ''
  const d = new Date(ts)
  const y = d.getFullYear()
  return Number.isFinite(y) ? String(y) : ''
})

const isSelfProfile = computed(() => !!meUserId.value && meUserId.value === Number(userId.value))
const followStatusText = computed(() =>
  describeFollowStatusText({
    followStatus: followStatus.value,
    followStatusState: followStatusState.value,
    authed: authed.value,
    isSelf: isSelfProfile.value
  })
)

const communitySignals = computed(() =>
  buildCommunitySignals({
    profile: profile.value,
    joinedYear: joinedYear.value,
    socialDegraded: socialDegraded.value,
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

const profileTimeline = computed(() =>
  buildProfileTimeline({
    posts: recentPosts.value,
    comments: recentComments.value,
    usersById: timelineUsers.value,
    limit: 6
  })
)

function categoryLabel(id) {
  const cid = Number(id || 0)
  if (!cid) return ''
  const category = taxonomy.categoriesById.get(cid)
  return category?.name || `分类#${cid}`
}

async function loadProfile() {
  error.value = ''
  loading.value = true
  try {
    profile.value = await getUserProfile(userId.value, { force: true })
    emit('trace', profile.value?._traceId || '')
  } catch (e) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadRecentPosts() {
  try {
    const { data, traceId } = await listUserRecentPosts(userId.value, { page: 0, size: 3 })
    recentPosts.value = Array.isArray(data) ? data : []
    emit('trace', traceId || '')
  } catch {
    recentPosts.value = []
  }
}

async function loadRecentComments() {
  try {
    const { data, traceId } = await listUserRecentComments(userId.value, { page: 0, size: 3 })
    recentComments.value = Array.isArray(data) ? data : []
    emit('trace', traceId || '')
  } catch {
    recentComments.value = []
  }
}

async function loadTimelineUsers() {
  const ids = collectTimelineUserIds({
    posts: recentPosts.value,
    comments: recentComments.value
  })

  if (ids.length === 0) {
    timelineUsers.value = {}
    return
  }

  try {
    timelineUsers.value = await postMetaCache.ensureUserSummaries(ids)
  } catch {
    timelineUsers.value = {}
  }
}

async function loadFollowStatus() {
  if (!authed.value || !meUserId.value || meUserId.value === Number(userId.value)) {
    followStatus.value = null
    followStatusState.value = 'idle'
    return
  }
  followStatusState.value = 'loading'
  try {
    const resp = await getFollowStatus(3, Number(userId.value), { force: true })
    emit('trace', resp?.traceId || '')
    followStatus.value = resp?.data ?? null
    followStatusState.value = typeof resp?.data === 'boolean' ? 'ready' : 'error'
  } catch {
    followStatus.value = null
    followStatusState.value = 'error'
  }
}

async function doFollow(follow) {
  actionLoading.value = true
  try {
    if (follow) {
      const resp = await followUser(3, Number(userId.value), Number(userId.value))
      emit('trace', resp?.traceId || '')
    } else {
      const resp = await unfollowUser(3, Number(userId.value))
      emit('trace', resp?.traceId || '')
    }
    await loadFollowStatus()
    await loadProfile()
  } catch (e) {
    error.value = e?.message || '关注操作失败'
  } finally {
    actionLoading.value = false
  }
}

async function reload() {
  await loadProfile()
  await loadFollowStatus()
  await loadRecentPosts()
  await loadRecentComments()
  await loadTimelineUsers()
}

onMounted(reload)
watch(userId, reload)
onMounted(() => {
  taxonomy.ensureCategories()
})

watch(
  () => auth.authed,
  (v) => {
    if (v) prefs.ensureBlocked(true)
    else prefs.clear()
  },
  { immediate: true }
)

async function toggleBlock() {
  const targetId = Number(userId.value || 0)
  if (!targetId || !authed.value || !meUserId.value || meUserId.value === targetId) return

  actionLoading.value = true
  try {
    if (isBlocked.value) {
      await unblockUser(targetId)
      if (typeof window !== 'undefined' && window.$toast) {
        window.$toast({ type: 'success', text: '已解除屏蔽' })
      }
    } else {
      await blockUser(targetId)
      if (typeof window !== 'undefined' && window.$toast) {
        window.$toast({ type: 'success', text: '已屏蔽该用户' })
      }
    }
    await prefs.ensureBlocked(true)
  } catch (e) {
    error.value = e?.message || '操作失败'
  } finally {
    actionLoading.value = false
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 980px;
}

.profile-eyebrow,
.profile-cover-kicker,
.profile-info-kicker-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-3);
}

.profile-card {
  padding: 0;
  overflow: hidden;
  border-radius: 28px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  box-shadow: var(--shadow-md);
}

.profile-cover {
  position: relative;
  min-height: 240px;
  padding: 24px;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  background:
    radial-gradient(circle at top left, color-mix(in srgb, var(--accent) 26%, transparent 74%), transparent 42%),
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 18%, var(--surface) 82%), color-mix(in srgb, var(--accent) 8%, var(--bg) 92%));
  border-bottom: 1px solid var(--border);
}

.profile-cover-sheet {
  max-width: 360px;
  display: grid;
  gap: 10px;
  padding: 20px;
  border-radius: 24px;
  background: color-mix(in srgb, var(--surface) 88%, white 12%);
  border: 1px solid color-mix(in srgb, var(--border) 70%, transparent 30%);
  box-shadow: var(--shadow-sm);
}

.profile-cover-title {
  font-family: var(--font-sans);
  font-size: clamp(28px, 3vw, 40px);
  line-height: 1.08;
  color: var(--text-1);
}

.profile-cover-subtitle {
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.75;
}

.profile-body {
  position: relative;
  margin-top: -56px;
  padding-bottom: 24px;
}

.profile-avatar-wrapper {
  padding-left: 24px;
}

.profile-avatar-wrapper :deep(.avatar) {
  border: 4px solid var(--surface);
  background: var(--surface);
  box-shadow: var(--shadow-md);
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  padding: 16px 24px 0 0;
  gap: 12px;
  flex-wrap: wrap;
}

.profile-action-btn {
  min-width: 96px;
}

.profile-message-link {
  align-self: center;
}

.profile-info {
  margin-top: 20px;
  padding: 0 24px;
}

.profile-info-kicker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.profile-info-kicker-meta {
  font-size: 12px;
  color: var(--text-3);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.profile-name-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.profile-name {
  margin: 0;
  font-family: var(--font-sans);
  font-size: clamp(32px, 3vw, 42px);
  font-weight: 800;
  line-height: 1.05;
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
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 94%, var(--bg) 6%), var(--surface));
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
  border-radius: 20px;
  padding: 18px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 94%, var(--bg) 6%), var(--surface)),
    repeating-linear-gradient(
      180deg,
      transparent,
      transparent 26px,
      color-mix(in srgb, var(--border) 11%, transparent 89%) 26px,
      color-mix(in srgb, var(--border) 11%, transparent 89%) 27px
    );
  display: grid;
  gap: 8px;
}

.profile-summary-label {
  font-size: 12px;
  color: var(--text-3);
}

.profile-summary-value {
  font-family: "Iowan Old Style", "Palatino Linotype", "Book Antiqua", Georgia, serif;
  font-size: 28px;
  font-weight: 800;
  color: var(--text-1);
  line-height: 1.2;
}

.profile-summary-text {
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-2);
}

.profile-signal-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.profile-post-feed {
  display: grid;
  gap: 12px;
}

.profile-post-card {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 94%, var(--bg) 6%), var(--surface));
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
}

.profile-post-meta {
  font-size: 12px;
  color: var(--text-3);
  letter-spacing: 0.04em;
}

.profile-signal-card {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid color-mix(in srgb, var(--border) 76%, transparent 24%);
  background: linear-gradient(180deg, color-mix(in srgb, var(--surface) 94%, var(--bg) 6%), var(--surface));
}

.profile-signal-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-3);
}

.profile-signal-value {
  font-family: var(--font-sans);
  font-size: clamp(22px, 2.2vw, 28px);
  font-weight: 800;
  line-height: 1.1;
  color: var(--text-1);
}

.profile-signal-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-2);
}

.profile-next-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 768px) {
  .profile-body {
    margin-top: -48px;
  }

  .profile-cover {
    min-height: 220px;
    padding: 16px;
  }

  .profile-avatar-wrapper {
    padding-left: 16px;
  }

  .profile-actions {
    padding: 12px 16px 0 16px;
    justify-content: flex-start;
  }

  .profile-info {
    padding: 0 16px;
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

  .profile-post-feed,
  .profile-signal-grid {
    grid-template-columns: 1fr;
  }
}
</style>
