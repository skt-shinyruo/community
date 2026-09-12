// 浏览器标签标题随路由更新：消费路由 meta.title，基线标题与 index.html 保持一致。

const BASE_TITLE = 'Community'

export function resolveDocumentTitle(to) {
  const title = typeof to?.meta?.title === 'string' ? to.meta.title.trim() : ''
  return title ? `${title} - ${BASE_TITLE}` : BASE_TITLE
}

export function applyDocumentTitle(to, doc = typeof document !== 'undefined' ? document : null) {
  if (!doc) return
  doc.title = resolveDocumentTitle(to)
}
