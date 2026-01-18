<!-- RightPanel：右侧上下文面板（提示、快捷键、当前页面信息）。 -->
<template>
  <div class="right-panel">
    <div class="right-panel-header">
      <div style="font-weight: 900">上下文面板</div>
      <button class="btn ghost" @click="ui.toggleRightPanel" title="关闭面板">×</button>
    </div>

    <div class="right-panel-body">
      <div class="card flat">
        <div class="stack" style="gap: 10px">
          <div style="font-weight: 800">快捷键</div>
          <div class="muted" style="font-size: 12px">
            <span class="kbd">{{ isMac ? '⌘' : 'Ctrl' }}</span> + <span class="kbd">K</span>
            聚焦全局搜索
          </div>
        </div>
      </div>

      <div class="card flat">
        <div class="stack" style="gap: 10px">
          <div style="font-weight: 800">当前页面</div>
          <div class="muted" style="font-size: 12px">name={{ String(route.name || '-') }}</div>
          <div class="muted" style="font-size: 12px; word-break: break-all">path={{ route.path }}</div>
        </div>
      </div>

      <div class="card flat">
        <div class="stack" style="gap: 10px">
          <div style="font-weight: 800">使用提示</div>
          <div class="muted" style="font-size: 12px">{{ hint }}</div>
          <div class="row" style="flex-wrap: wrap">
            <span class="tag">theme={{ ui.theme }}</span>
            <span class="tag">density={{ ui.density }}</span>
            <span class="tag" v-if="auth.authed">user={{ auth.username || auth.userId }}</span>
            <span class="tag" v-else>未登录</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform || '')

const hint = computed(() => {
  const name = String(route.name || '')
  if (name === 'posts') return '建议使用「latest/hot」排序快速扫读；点击标题进入详情。'
  if (name === 'postDetail') return '帖子详情采用文档化排版；评论区支持回复树展开与点赞。'
  if (name === 'search') return '支持关键词高亮；可在顶部全局搜索直接跳转并触发查询。'
  if (name === 'messages' || name === 'messageDetail') return '私信内容属于个人隐私，请谨慎分享截图与链接。'
  if (name === 'notices' || name === 'noticeDetail') return '通知支持分页与标记已读；建议定期处理未读。'
  if (name === 'analytics') return '统计页仅管理员/版主可见；如无权限请联系管理员。'
  if (name === 'settings') return '设置页包含头像更新；本地环境可用“仅回写 fileName”联调链路。'
  return '使用左侧导航切换模块；顶部栏可切换主题/密度并快速搜索。'
})
</script>

