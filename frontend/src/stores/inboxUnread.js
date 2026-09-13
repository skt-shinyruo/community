// 壳层未读角标：聚合通知与私信的未读计数，供侧边栏与移动端导航渲染角标。
// 刷新触发点：登录恢复 / 登出（身份变化）、窗口重新聚焦、已读操作、IM 实时私信事件；
// 不做轮询，失败静默（后台请求不弹全局错误 toast）。

import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { identityScope } from './identityScope'
import { topicSummary } from '../api/services/noticeService'
import { getImUnreadSummary } from '../api/services/imCoreChatService'

// store 实例 -> 在途刷新（scope + promise）：同一身份 scope 的并发触发复用同一轮请求。
const inFlightRefreshes = new WeakMap()

export function formatUnreadCount(count) {
  const n = Number(count || 0)
  if (!Number.isFinite(n) || n <= 0) return ''
  return n > 99 ? '99+' : String(n)
}

function sumUnread(items) {
  return (Array.isArray(items) ? items : []).reduce((total, item) => {
    const n = Number(item?.unreadCount || 0)
    return Number.isFinite(n) && n > 0 ? total + n : total
  }, 0)
}

export const useInboxUnreadStore = defineStore('inboxUnread', {
  state: () => ({
    noticeUnread: 0,
    messageUnread: 0,
    requestId: 0
  }),
  actions: {
    reset() {
      this.noticeUnread = 0
      this.messageUnread = 0
      this.requestId += 1
    },

    async refresh() {
      const auth = useAuthStore()
      if (!auth.authed) {
        this.reset()
        return
      }
      const scope = identityScope(auth)
      // 登录 / 会话恢复瞬间身份 watcher、窗口聚焦、落地页首载等触发点可能同时开火：
      // 同一身份 scope 下复用在途刷新，只发起一轮请求；完成后的新触发正常再刷。
      const inFlight = inFlightRefreshes.get(this)
      if (inFlight && inFlight.scope === scope) return inFlight.promise
      const requestId = ++this.requestId
      const promise = Promise.allSettled([
        topicSummary({ silent: true }),
        getImUnreadSummary()
      ]).then(([notices, im]) => {
        // 会话切换或更新的刷新已发起时，丢弃本次过期结果。
        if (requestId !== this.requestId) return
        if (scope !== identityScope(useAuthStore())) return
        if (notices.status === 'fulfilled') this.noticeUnread = sumUnread(notices.value?.data)
        if (im.status === 'fulfilled') this.messageUnread = sumUnread(im.value?.conversations)
      }).finally(() => {
        if (inFlightRefreshes.get(this)?.promise === promise) inFlightRefreshes.delete(this)
      })
      inFlightRefreshes.set(this, { scope, promise })
      return promise
    }
  }
})
