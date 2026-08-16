import { normalizeOpaqueId } from '../utils/opaqueId'

export function buildQuotePreview(text) {
  const normalized = String(text || '').replace(/\s+/g, ' ').trim()
  if (!normalized) return ''
  return normalized.length > 120 ? `${normalized.slice(0, 120)}…` : normalized
}

export function buildQuoteMarkdown(quote) {
  const raw = String(quote?.raw || '').trim()
  if (!raw) return ''

  const username = String(quote?.username || '').trim()
  const userId = normalizeOpaqueId(quote?.userId)
  const who = username ? `@${username}` : userId ? `成员 ${userId}` : '用户'
  const lines = raw
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .slice(0, 6)

  const header = `> 引用 ${who}`
  const body = lines.map((line) => `> ${line}`).join('\n')
  return body ? `${header}\n${body}` : header
}

export function composeReplyContent(draft, quote) {
  const draftContent = String(draft || '').trim()
  const quoteMarkdown = quote ? buildQuoteMarkdown(quote) : ''
  if (!quoteMarkdown) return draftContent
  if (!draftContent) return quoteMarkdown
  return `${quoteMarkdown}\n\n${draftContent}`
}
