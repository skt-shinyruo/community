<template>
  <div class="page" style="padding: 0; max-width: 1000px; margin: 0 auto">
    <div style="background: var(--surface); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); overflow: hidden">
      <!-- Cover & Avatar -->
      <div class="profile-cover"></div>

      <div style="position: relative; padding-bottom: 24px">
        <div class="profile-avatar-wrapper">
          <UiAvatar :src="profile?.headerUrl || ''" :name="profile?.username || ''" :size="120" style="font-size: 48px" />
        </div>

        <!-- Actions -->
        <div class="row" style="justify-content: flex-end; padding: 16px 24px 0 0; gap: 12px">
          <template v-if="authed && meUserId && meUserId !== Number(userId)">
            <UiButton v-if="followStatus === false" @click="doFollow(true)" :disabled="actionLoading" class="primary">Follow</UiButton>
            <UiButton variant="secondary" v-else-if="followStatus === true" @click="doFollow(false)" :disabled="actionLoading">Unfollow</UiButton>
            <UiButton variant="secondary" v-else disabled>Querying...</UiButton>
            <RouterLink class="btn-icon" :to="`/messages`" title="发私信">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
            </RouterLink>
          </template>
          <template v-if="authed && meUserId === Number(userId)">
            <RouterLink class="btn secondary" to="/settings">Edit Profile</RouterLink>
          </template>
        </div>

        <!-- Info -->
        <div style="margin-top: 20px; padding: 0 24px">
          <h1 style="margin: 0; font-size: 28px; font-weight: 800">{{ profile?.username || `user#${profile?.id}` }}</h1>
          <div class="muted" style="margin-top: 4px">User ID: {{ userId }} · Joined 2026</div>
        </div>

        <!-- Stats -->
        <div class="profile-stats-bar">
          <div class="profile-stat">
            <span class="profile-stat-val">{{ profile?.likeCount || 0 }}</span>
            <span class="profile-stat-label">Likes</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-val">{{ profile?.followeeCount || 0 }}</span>
            <span class="profile-stat-label">Following</span>
          </div>
          <div class="profile-stat">
            <span class="profile-stat-val">{{ profile?.followerCount || 0 }}</span>
            <span class="profile-stat-label">Followers</span>
          </div>
        </div>

        <!-- Mock Tabs -->
        <div class="row" style="padding: 0 24px; gap: 32px; border-bottom: 1px solid var(--border)">
          <div style="padding: 12px 0; border-bottom: 2px solid var(--accent); font-weight: 600; color: var(--text-1); cursor: pointer">Posts</div>
          <div style="padding: 12px 0; border-bottom: 2px solid transparent; font-weight: 500; color: var(--muted); cursor: pointer">Comments</div>
          <div style="padding: 12px 0; border-bottom: 2px solid transparent; font-weight: 500; color: var(--muted); cursor: pointer">Likes</div>
        </div>

        <!-- Content Area (Placeholder) -->
        <div style="padding: 24px; min-height: 200px">
          <UiEmpty>暂无动态</UiEmpty>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'
import { getUserProfile } from '../api/services/userService'
import { followUser, unfollowUser, getFollowStatus } from '../api/services/socialService'
import UiButton from '../components/ui/UiButton.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'

const emit = defineEmits(['trace'])

const route = useRoute()
const auth = useAuthStore()
const authed = computed(() => !!auth.accessToken)

const userId = computed(() => String(route.params.userId || ''))

const profile = ref(null)
const loading = ref(false)
const error = ref('')

const meUserId = computed(() => Number(auth.userId || 0))
const actionLoading = ref(false)
const followStatus = ref(null)

const avatarFileName = ref('')
const uploadToken = ref('')
const uploadFileName = ref('')
const uploadBucketUrl = ref('')

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

async function loadFollowStatus() {
  if (!authed.value || !meUserId.value || meUserId.value === Number(userId.value)) {
    followStatus.value = null
    return
  }
  try {
    const resp = await getFollowStatus(3, Number(userId.value), { force: true })
    emit('trace', resp?.traceId || '')
    followStatus.value = resp?.data ?? null
  } catch {
    followStatus.value = null
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
}

onMounted(reload)
</script>
