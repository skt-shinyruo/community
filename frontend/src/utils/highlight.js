// 高亮内容安全渲染：默认转义所有标签，仅放行 <em> 与 </em>。

export function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function emOnlyHtml(text) {
  const escaped = escapeHtml(text)
  return escaped.replace(/&lt;\/?em&gt;/g, (m) => (m === '&lt;em&gt;' ? '<em>' : '</em>'))
}

