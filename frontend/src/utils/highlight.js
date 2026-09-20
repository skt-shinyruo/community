import { escapeHtml } from './escapeHtml'

// 搜索高亮安全渲染：默认转义所有标签，仅放行成对的精确 <em>…</em>。
// 这是 SearchView v-html 路径的 HTML 白名单边界：带属性的 <em>、孤立的开/闭标签都保持转义。
export function emOnlyHtml(text) {
  const escaped = escapeHtml(text)
  return escaped.replace(/&lt;em&gt;(.*?)&lt;\/em&gt;/g, '<em>$1</em>')
}
