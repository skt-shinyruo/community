<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>用户主页</template>
        <template #subtitle>userId={{ userId }}</template>
        <template #actions>
          <UiButton variant="secondary" @click="reload" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>

      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>
      <UiEmpty v-else-if="!profile" style="margin-top: 12px">暂无数据</UiEmpty>

      <div v-else class="stack" style="margin-top: 12px">
        <div class="row" style="gap: 12px; align-items: center; flex-wrap: wrap">
          <UiAvatar :src="profile.headerUrl || ''" :name="profile.username || ''" :size="44" />
          <div class="stack" style="gap: 6px">
            <div style="font-weight: 900; font-size: 18px; line-height: 1.35">{{ profile.username || `user#${profile.id}` }}</div>
            <div class="row muted" style="gap: 8px; flex-wrap: wrap; font-size: 12px">
              <UiBadge variant="default">获赞 {{ Number(profile.likeCount || 0) }}</UiBadge>
              <UiBadge variant="default">关注 {{ Number(profile.followeeCount || 0) }}</UiBadge>
              <UiBadge variant="default">粉丝 {{ Number(profile.followerCount || 0) }}</UiBadge>
            </div>
          </div>
        </div>

        <div class="row" style="flex-wrap: wrap">
          <RouterLink class="btn secondary" :to="`/users/${userId}/followees`">关注列表</RouterLink>
          <RouterLink class="btn secondary" :to="`/users/${userId}/followers`">粉丝列表</RouterLink>
        </div>

        <div class="row" v-if="authed && meUserId && meUserId !== Number(userId)" style="flex-wrap: wrap">
          <UiButton v-if="followStatus === false" @click="doFollow(true)" :disabled="actionLoading">关注</UiButton>
          <UiButton variant="secondary" v-else-if="followStatus === true" @click="doFollow(false)" :disabled="actionLoading">取关</UiButton>
          <UiButton variant="secondary" v-else disabled>关注状态查询中…</UiButton>
          <UiBadge v-if="followStatus !== null" :variant="followStatus ? 'success' : 'default'">关注：{{ followStatusText }}</UiBadge>
        </div>
      </div>
    </UiCard>

    <UiCard v-if="authed && meUserId === Number(userId)">
      <UiPageHeader>
        <template #title>头像设置</template>
        <template #subtitle>PUT /api/users/{{ userId }}/avatar</template>
        <template #actions>
          <UiButton @click="updateAvatar" :disabled="actionLoading || !avatarFileName">更新</UiButton>
        </template>
      </UiPageHeader>

      <div class="row" style="margin-top: 12px; flex-wrap: wrap">
        <UiInput v-model.trim="avatarFileName" placeholder="fileName（上传到七牛后的 key）" style="min-width: 260px; flex: 1" />
        <UiButton variant="secondary" @click="loadUploadToken" :disabled="actionLoading">获取上传凭证</UiButton>
      </div>

      <div v-if="uploadToken" class="muted" style="margin-top: 12px; font-size: 12px">
        uploadToken 已生成（长度={{ uploadToken.length }}），fileName={{ uploadFileName }}，bucket={{ uploadBucketUrl }}
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import http from '../api/http'
import { getUserProfile } from '../api/services/userService'
import { followUser, unfollowUser, getFollowStatus } from '../api/services/socialService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiAvatar from '../components/ui/UiAvatar.vue'
import UiBadge from '../components/ui/UiBadge.vue'
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

const followStatusText = computed(() => {
  if (followStatus.value === null) return '-'
  return followStatus.value ? '已关注' : '未关注'
})

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

async function loadUploadToken() {
  actionLoading.value = true
  try {
    const resp = await http.get(`/api/users/${userId.value}/avatar/upload-token`)
    uploadToken.value = resp?.data?.data?.uploadToken || ''
    uploadFileName.value = resp?.data?.data?.fileName || ''
    uploadBucketUrl.value = resp?.data?.data?.bucketUrl || ''
    avatarFileName.value = uploadFileName.value || avatarFileName.value
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    error.value = e?.response?.data?.message || '获取上传凭证失败'
  } finally {
    actionLoading.value = false
  }
}

async function updateAvatar() {
  if (!avatarFileName.value) return
  actionLoading.value = true
  try {
    const resp = await http.put(`/api/users/${userId.value}/avatar`, { fileName: avatarFileName.value })
    emit('trace', resp?.data?.traceId || '')
    await loadProfile()
  } catch (e) {
    error.value = e?.response?.data?.message || '更新头像失败'
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
