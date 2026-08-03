<template>
  <div class="user-card-wrapper" @mouseenter="onEnter" @mouseleave="hide">
    <slot />
    
    <Transition name="pop">
      <div v-if="show && cardUser" class="user-card-popover" :style="{ left: align === 'right' ? 'auto' : '0', right: align === 'right' ? '0' : 'auto' }">
        <div class="row" style="gap: 12px; align-items: flex-start">
           <UiAvatar :src="cardUser.headerUrl" :name="cardUser.username" :size="48" />
           <div style="flex: 1">
              <div class="row" style="gap: 8px; align-items: center; flex-wrap: wrap">
                <div style="font-weight: 700; font-size: 16px; color: var(--text-1)">{{ cardUser.username }}</div>
                <UiRoleBadge :user="cardUser" />
              </div>
              <div class="muted" style="font-size: 12px">加入 {{ new Date(cardUser.createTime || Date.now()).getFullYear() }}</div>
              
              <div class="row" style="gap: 12px; margin-top: 8px; font-size: 13px">
                 <div class="stat-item">
                    <b>{{ cardUser.postCount || 0 }}</b> 帖子
                 </div>
                 <div class="stat-item">
                    <b>{{ cardUser.likeCount || 0 }}</b> 获赞
                 </div>
              </div>

              <div class="row" style="gap: 8px; margin-top: 12px; flex-wrap: wrap">
                <RouterLink
                  v-if="resolvedUserId"
                  class="btn secondary"
                  style="height: 32px; padding: 0 12px; font-size: 12px"
                  :to="{ name: 'userProfile', params: { userId: String(resolvedUserId) } }"
                >
                  查看主页
                </RouterLink>

                <template v-if="canInteract">
                  <UiButton
                    variant="secondary"
                    style="height: 32px; padding: 0 12px; font-size: 12px"
                    :disabled="actionLoading"
                    @click.stop="openReport"
                  >
                    举报
                  </UiButton>
                  <UiButton
                    :variant="isBlocked ? 'dangerSecondary' : 'secondary'"
                    style="height: 32px; padding: 0 12px; font-size: 12px"
                    :disabled="actionLoading"
                    @click.stop="toggleBlock"
                  >
                    {{ isBlocked ? '已屏蔽' : '屏蔽' }}
                  </UiButton>
                </template>
              </div>
           </div>
        </div>
      </div>
    </Transition>
  </div>

  <ReportModal
    v-if="reportOpen"
    target-type="user"
    :target-id="resolvedUserId"
    @close="reportOpen = false"
    @submitted="reportOpen = false"
  />
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { blockUser, unblockUser } from '../../api/services/blockService'
import { getUserProfile } from '../../api/services/userService'
import { showToast } from '../../ui/toastService'
import UiAvatar from './UiAvatar.vue'
import UiButton from './UiButton.vue'
import UiRoleBadge from './UiRoleBadge.vue'
import ReportModal from '../modals/ReportModal.vue'
import { hasOpaqueId, normalizeOpaqueId, sameOpaqueId } from '../../utils/opaqueId'

const props = defineProps({
  user: { type: Object, default: null },
  align: { type: String, default: 'left' }
})

const auth = useAuthStore()
const prefs = useSocialPrefsStore()

const resolvedUserId = computed(() => {
  const u = props.user || null
  return normalizeOpaqueId(u?.id)
})

const profile = ref(null)
const profileLoading = ref(false)

const cardUser = computed(() => profile.value || props.user || null)

const meUserId = computed(() => normalizeOpaqueId(auth.userId))
const canInteract = computed(() => !!auth.authed && hasOpaqueId(resolvedUserId.value) && !sameOpaqueId(resolvedUserId.value, meUserId.value))
const isBlocked = computed(() => prefs.blockedSet.has(resolvedUserId.value))

const show = ref(false)
let timer = null

const reportOpen = ref(false)
const actionLoading = ref(false)
let profileRequestId = 0
let actionRequestId = 0
let disposed = false

function isCurrentScope(requestId, uid, authGeneration) {
  return !disposed
    && requestId === profileRequestId
    && uid === resolvedUserId.value
    && auth.tokenGeneration === authGeneration
}

function isCurrentAction(requestId, uid, authGeneration) {
  return !disposed
    && requestId === actionRequestId
    && uid === resolvedUserId.value
    && auth.tokenGeneration === authGeneration
}

function shouldFetchProfile(user) {
  if (!user) return true
  // batch-summary returns a subset (no createTime/likeCount). Popover needs richer fields.
  if (!user.createTime) return true
  if (typeof user.likeCount === 'undefined') return true
  return false
}

async function ensureProfile() {
  const uid = resolvedUserId.value
  if (!uid) return
  const requestId = ++profileRequestId
  const authGeneration = auth.tokenGeneration
  if (profile.value && !shouldFetchProfile(profile.value)) return
  if (!shouldFetchProfile(props.user)) {
    // props already has a rich profile
    if (isCurrentScope(requestId, uid, authGeneration)) profile.value = props.user
    return
  }

  profileLoading.value = true
  try {
    const nextProfile = await getUserProfile(uid).catch(() => null)
    if (isCurrentScope(requestId, uid, authGeneration)) profile.value = nextProfile
  } finally {
    if (isCurrentScope(requestId, uid, authGeneration)) profileLoading.value = false
  }
}

async function onEnter() {
  if (timer) window.clearTimeout(timer)
  show.value = true
  ensureProfile()
  if (!auth.authed) return
  try {
    await prefs.ensureBlocked()
  } catch {
    // ignore
  }
}

function hide() {
  timer = setTimeout(() => {
    show.value = false
  }, 300)
}

watch(resolvedUserId, () => {
  profileRequestId += 1
  actionRequestId += 1
  profileLoading.value = false
  profile.value = null
  reportOpen.value = false
})

watch(() => auth.tokenGeneration, () => {
  profileRequestId += 1
  actionRequestId += 1
  profileLoading.value = false
  profile.value = null
  reportOpen.value = false
  actionLoading.value = false
})

function openReport() {
  if (!auth.authed) {
    showToast({ type: 'warning', text: '请先登录' })
    return
  }
  reportOpen.value = true
}

async function toggleBlock() {
  if (!canInteract.value) return
  const targetId = resolvedUserId.value
  const authGeneration = auth.tokenGeneration
  const requestId = ++actionRequestId
  actionLoading.value = true
  try {
    if (isBlocked.value) {
      await unblockUser(targetId)
      if (!isCurrentAction(requestId, targetId, authGeneration)) return
      showToast({ type: 'success', text: '已解除屏蔽' })
    } else {
      await blockUser(targetId)
      if (!isCurrentAction(requestId, targetId, authGeneration)) return
      showToast({ type: 'success', text: '已屏蔽该用户' })
    }
    await prefs.ensureBlocked(true)
  } catch (e) {
    if (!isCurrentAction(requestId, targetId, authGeneration)) return
    showToast({ type: 'error', title: '操作失败', text: e?.message || '请稍后重试' })
  } finally {
    if (isCurrentAction(requestId, targetId, authGeneration)) actionLoading.value = false
  }
}

onBeforeUnmount(() => {
  disposed = true
  profileRequestId += 1
  actionRequestId += 1
})
</script>

<style scoped>
.user-card-wrapper {
  position: relative;
  display: inline-block;
}

.user-card-popover {
  position: absolute;
  top: 100%;
  margin-top: 8px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-lg);
  border-radius: var(--radius-md);
  padding: 16px;
  width: 280px;
  z-index: 1000;
}

.stat-item b {
  color: var(--text-1);
}
.stat-item {
  color: var(--text-2);
}

.pop-enter-active,
.pop-leave-active {
  transition: all 0.2s;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}
</style>
