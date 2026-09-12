// 懒加载 chunk（动态 import）失败的识别与用户可见反馈：发版后驻留的旧标签页仍引用
// 已被替换的 chunk，导航时 import() 失败且导航被静默取消；识别后提示引导刷新。

import { showToast } from '../ui/toastService'

const CHUNK_LOAD_MESSAGE_PATTERNS = [
  /Failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /Importing a module script failed/i,
  /Loading (?:CSS )?chunk \S+ failed/i
]

export function isChunkLoadFailure(error) {
  if (error == null) return false
  if (error.name === 'ChunkLoadError') return true
  const message = typeof error === 'string' ? error : typeof error?.message === 'string' ? error.message : ''
  return CHUNK_LOAD_MESSAGE_PATTERNS.some((pattern) => pattern.test(message))
}

export function handleChunkLoadFailure(error, { toast = showToast, reload = () => window.location.reload() } = {}) {
  if (!isChunkLoadFailure(error)) return false
  toast({
    type: 'warning',
    title: '页面加载失败',
    text: '应用可能刚发布了新版本，请刷新页面后重试。',
    duration: 0,
    actionText: '刷新页面',
    onAction: () => reload()
  })
  return true
}
