<template>
  <div class="page">
    <div class="card">
      <div class="stack">
        <div class="row" style="justify-content: space-between">
          <div>
            <div style="font-weight: 700">登录态</div>
            <div class="muted">用于联调：调用 /api/auth/me</div>
          </div>
          <button class="btn" @click="load">刷新</button>
        </div>

        <div v-if="loading" class="muted">加载中...</div>
        <div v-else-if="error" class="error">{{ error }}</div>
        <pre v-else style="margin: 0">{{ JSON.stringify(me, null, 2) }}</pre>
      </div>
    </div>

    <div class="card">
      <div class="stack">
        <div class="row" style="justify-content: space-between">
          <div>
            <div style="font-weight: 700">迭代 1：搜索</div>
            <div class="muted">调用 /api/search/posts</div>
          </div>
          <button class="btn" @click="doSearch" :disabled="searchLoading">
            {{ searchLoading ? '搜索中...' : '搜索' }}
          </button>
        </div>

        <div class="row">
          <input class="input" v-model.trim="searchKeyword" placeholder="keyword" />
        </div>

        <div v-if="searchLoading" class="muted">加载中...</div>
        <div v-else-if="searchError" class="error">{{ searchError }}</div>
        <pre v-else style="margin: 0">{{ JSON.stringify(searchResult, null, 2) }}</pre>
      </div>
    </div>

    <div class="card">
      <div class="stack">
        <div class="row" style="justify-content: space-between">
          <div>
            <div style="font-weight: 700">迭代 1：通知</div>
            <div class="muted">调用 /api/notices/**</div>
          </div>
          <button class="btn" @click="loadNotices" :disabled="noticeLoading">
            {{ noticeLoading ? '加载中...' : '刷新' }}
          </button>
        </div>

        <div class="row">
          <select class="input" v-model="noticeTopic">
            <option value="like">like</option>
            <option value="comment">comment</option>
            <option value="follow">follow</option>
          </select>
          <div class="muted" style="align-self: center">未读：{{ unreadCount }}</div>
        </div>

        <div v-if="noticeLoading" class="muted">加载中...</div>
        <div v-else-if="noticeError" class="error">{{ noticeError }}</div>
        <pre v-else style="margin: 0">{{ JSON.stringify(notices, null, 2) }}</pre>
      </div>
    </div>

    <div class="card">
      <div class="stack">
        <div class="row" style="justify-content: space-between">
          <div>
            <div style="font-weight: 700">迭代 1：统计</div>
            <div class="muted">调用 /api/analytics/uv 与 /api/analytics/dau</div>
          </div>
          <button class="btn" @click="loadAnalytics" :disabled="analyticsLoading">
            {{ analyticsLoading ? '查询中...' : '查询' }}
          </button>
        </div>

        <div class="row">
          <input class="input" type="date" v-model="analyticsStart" />
          <input class="input" type="date" v-model="analyticsEnd" />
        </div>

        <div v-if="analyticsLoading" class="muted">加载中...</div>
        <div v-else-if="analyticsError" class="error">{{ analyticsError }}</div>
        <pre v-else style="margin: 0">{{ JSON.stringify(analyticsResult, null, 2) }}</pre>
      </div>
    </div>

    <div class="card">
      <div class="stack">
        <div class="row" style="justify-content: space-between">
          <div>
            <div style="font-weight: 700">迭代 2：社交（点赞/关注）</div>
            <div class="muted">调用 /api/likes/** 与 /api/follows/**</div>
          </div>
          <button class="btn" @click="refreshSocial" :disabled="socialLoading">
            {{ socialLoading ? '刷新中...' : '刷新' }}
          </button>
        </div>

        <div class="row">
          <div class="muted" style="align-self: center">当前登录 userId：{{ me?.userId ?? '-' }}</div>
        </div>

        <div style="font-weight: 700">点赞（Like）</div>
        <div class="row">
          <input class="input" v-model.number="likeEntityType" placeholder="entityType" />
          <input class="input" v-model.number="likeEntityId" placeholder="entityId" />
          <input class="input" v-model.number="likeEntityUserId" placeholder="entityUserId(被赞方)" />
        </div>
        <div class="row">
          <button class="btn" @click="doLike(true)" :disabled="socialLoading">点赞</button>
          <button class="btn" @click="doLike(false)" :disabled="socialLoading">取消点赞</button>
          <button class="btn" @click="loadLikeStatus" :disabled="socialLoading">状态</button>
          <button class="btn" @click="loadLikeCount" :disabled="socialLoading">计数</button>
          <button class="btn" @click="loadUserLikeCount" :disabled="socialLoading">用户获赞</button>
        </div>

        <div style="font-weight: 700">关注（Follow）</div>
        <div class="row">
          <input class="input" v-model.number="followTargetUserId" placeholder="targetUserId" />
        </div>
        <div class="row">
          <button class="btn" @click="doFollow" :disabled="socialLoading">关注</button>
          <button class="btn" @click="doUnfollow" :disabled="socialLoading">取关</button>
          <button class="btn" @click="loadFollowStatus" :disabled="socialLoading">是否关注</button>
          <button class="btn" @click="loadFollowCounts" :disabled="socialLoading">关注/粉丝数</button>
          <button class="btn" @click="loadFollowLists" :disabled="socialLoading">列表</button>
        </div>

        <div v-if="socialLoading" class="muted">加载中...</div>
        <div v-else-if="socialError" class="error">{{ socialError }}</div>
        <pre v-else style="margin: 0">{{ JSON.stringify(socialResult, null, 2) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const emit = defineEmits(['trace'])

const me = ref(null)
const loading = ref(false)
const error = ref('')

const searchKeyword = ref('')
const searchResult = ref([])
const searchLoading = ref(false)
const searchError = ref('')

const noticeTopic = ref('like')
const unreadCount = ref(0)
const notices = ref([])
const noticeLoading = ref(false)
const noticeError = ref('')

const today = new Date().toISOString().slice(0, 10)
const analyticsStart = ref(today)
const analyticsEnd = ref(today)
const analyticsResult = ref({ uv: null, dau: null })
const analyticsLoading = ref(false)
const analyticsError = ref('')

const likeEntityType = ref(1)
const likeEntityId = ref(100)
const likeEntityUserId = ref(2)

const followTargetUserId = ref(2)

const socialLoading = ref(false)
const socialError = ref('')
const socialResult = ref({})

async function load() {
  error.value = ''
  loading.value = true
  try {
    const resp = await http.get('/api/auth/me')
    me.value = resp?.data?.data || null
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    error.value = e?.response?.data?.message || '请求失败'
  } finally {
    loading.value = false
  }
}

async function doSearch() {
  searchError.value = ''
  searchLoading.value = true
  try {
    const resp = await http.get('/api/search/posts', {
      params: { keyword: searchKeyword.value || '', page: 0, size: 10 }
    })
    searchResult.value = resp?.data?.data || []
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    searchError.value = e?.response?.data?.message || '搜索失败'
  } finally {
    searchLoading.value = false
  }
}

async function loadNotices() {
  noticeError.value = ''
  noticeLoading.value = true
  try {
    const [unreadResp, listResp] = await Promise.all([
      http.get('/api/notices/unread-count', { params: { topic: noticeTopic.value } }),
      http.get('/api/notices', { params: { topic: noticeTopic.value, page: 0, size: 10 } })
    ])
    unreadCount.value = unreadResp?.data?.data ?? 0
    notices.value = listResp?.data?.data || []
    emit('trace', listResp?.data?.traceId || unreadResp?.data?.traceId || '')
  } catch (e) {
    noticeError.value = e?.response?.data?.message || '加载通知失败'
  } finally {
    noticeLoading.value = false
  }
}

async function loadAnalytics() {
  analyticsError.value = ''
  analyticsLoading.value = true
  try {
    const [uvResp, dauResp] = await Promise.all([
      http.get('/api/analytics/uv', { params: { start: analyticsStart.value, end: analyticsEnd.value } }),
      http.get('/api/analytics/dau', { params: { start: analyticsStart.value, end: analyticsEnd.value } })
    ])
    analyticsResult.value = { uv: uvResp?.data?.data ?? null, dau: dauResp?.data?.data ?? null }
    emit('trace', uvResp?.data?.traceId || dauResp?.data?.traceId || '')
  } catch (e) {
    analyticsError.value = e?.response?.data?.message || '查询失败'
  } finally {
    analyticsLoading.value = false
  }
}

onMounted(load)

async function refreshSocial() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const [likeStatusResp, likeCountResp, followStatusResp] = await Promise.all([
      http.get('/api/likes/status', {
        params: { entityType: likeEntityType.value, entityId: likeEntityId.value }
      }),
      http.get('/api/likes/count', { params: { entityType: likeEntityType.value, entityId: likeEntityId.value } }),
      http.get('/api/follows/status', { params: { entityType: 3, entityId: followTargetUserId.value } })
    ])
    socialResult.value = {
      likeStatus: likeStatusResp?.data?.data ?? null,
      likeCount: likeCountResp?.data?.data ?? null,
      followStatus: followStatusResp?.data?.data ?? null
    }
    emit('trace', likeStatusResp?.data?.traceId || likeCountResp?.data?.traceId || followStatusResp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '刷新失败'
  } finally {
    socialLoading.value = false
  }
}

async function doLike(liked) {
  socialError.value = ''
  socialLoading.value = true
  try {
    const resp = await http.post('/api/likes', {
      entityType: likeEntityType.value,
      entityId: likeEntityId.value,
      entityUserId: likeEntityUserId.value,
      liked
    })
    socialResult.value = resp?.data?.data || null
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '点赞请求失败'
  } finally {
    socialLoading.value = false
  }
}

async function loadLikeStatus() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const resp = await http.get('/api/likes/status', {
      params: { entityType: likeEntityType.value, entityId: likeEntityId.value }
    })
    socialResult.value = { liked: resp?.data?.data ?? null }
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '查询点赞状态失败'
  } finally {
    socialLoading.value = false
  }
}

async function loadLikeCount() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const resp = await http.get('/api/likes/count', {
      params: { entityType: likeEntityType.value, entityId: likeEntityId.value }
    })
    socialResult.value = { count: resp?.data?.data ?? null }
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '查询点赞数失败'
  } finally {
    socialLoading.value = false
  }
}

async function loadUserLikeCount() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const resp = await http.get(`/api/likes/users/${likeEntityUserId.value}/count`)
    socialResult.value = { userLikeCount: resp?.data?.data ?? null, userId: likeEntityUserId.value }
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '查询用户获赞失败'
  } finally {
    socialLoading.value = false
  }
}

async function doFollow() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const resp = await http.post('/api/follows', {
      entityType: 3,
      entityId: followTargetUserId.value,
      entityUserId: followTargetUserId.value
    })
    socialResult.value = { ok: true, action: 'follow', targetUserId: followTargetUserId.value, resp: resp?.data?.data ?? null }
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '关注失败'
  } finally {
    socialLoading.value = false
  }
}

async function doUnfollow() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const resp = await http.delete('/api/follows', { params: { entityType: 3, entityId: followTargetUserId.value } })
    socialResult.value = { ok: true, action: 'unfollow', targetUserId: followTargetUserId.value, resp: resp?.data?.data ?? null }
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '取关失败'
  } finally {
    socialLoading.value = false
  }
}

async function loadFollowStatus() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const resp = await http.get('/api/follows/status', { params: { entityType: 3, entityId: followTargetUserId.value } })
    socialResult.value = { following: resp?.data?.data ?? null }
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '查询关注状态失败'
  } finally {
    socialLoading.value = false
  }
}

async function loadFollowCounts() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const myUserId = me.value?.userId
    if (!myUserId) {
      socialError.value = '请先刷新登录态获取 userId'
      return
    }
    const [followeeResp, followerResp] = await Promise.all([
      http.get(`/api/follows/${myUserId}/followees/count`),
      http.get(`/api/follows/${followTargetUserId.value}/followers/count`)
    ])
    socialResult.value = {
      myFolloweeCount: followeeResp?.data?.data ?? null,
      targetFollowerCount: followerResp?.data?.data ?? null,
      myUserId,
      targetUserId: followTargetUserId.value
    }
    emit('trace', followeeResp?.data?.traceId || followerResp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '查询计数失败'
  } finally {
    socialLoading.value = false
  }
}

async function loadFollowLists() {
  socialError.value = ''
  socialLoading.value = true
  try {
    const myUserId = me.value?.userId
    if (!myUserId) {
      socialError.value = '请先刷新登录态获取 userId'
      return
    }
    const [followeeListResp, followerListResp] = await Promise.all([
      http.get(`/api/follows/${myUserId}/followees`, { params: { page: 0, size: 10 } }),
      http.get(`/api/follows/${followTargetUserId.value}/followers`, { params: { page: 0, size: 10 } })
    ])
    socialResult.value = {
      followees: followeeListResp?.data?.data || [],
      followers: followerListResp?.data?.data || [],
      myUserId,
      targetUserId: followTargetUserId.value
    }
    emit('trace', followeeListResp?.data?.traceId || followerListResp?.data?.traceId || '')
  } catch (e) {
    socialError.value = e?.response?.data?.message || '查询列表失败'
  } finally {
    socialLoading.value = false
  }
}
</script>
