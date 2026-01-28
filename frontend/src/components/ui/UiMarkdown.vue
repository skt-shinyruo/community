<template>
  <div class="markdown-body" :class="{ compact: variant === 'compact' }" v-html="rendered"></div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  content: { type: String, default: '' },
  // default：正文阅读模式；compact：评论/卡片内的紧凑渲染。
  variant: { type: String, default: 'default' }
})

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function sanitizeUrl(raw) {
  const url = String(raw || '').trim()
  if (!url) return ''
  const lower = url.toLowerCase()

  // 仅放行常见安全协议与相对路径，避免 javascript: 等注入。
  if (lower.startsWith('http://')) return url
  if (lower.startsWith('https://')) return url
  if (lower.startsWith('mailto:')) return url
  if (lower.startsWith('tel:')) return url
  if (url.startsWith('/') || url.startsWith('./') || url.startsWith('../') || url.startsWith('#')) return url
  return ''
}

function renderInline(escapedText) {
  let text = String(escapedText || '')

  // Inline code：先占位，避免后续 bold/italic 影响 code 内容。
  const inlineCodes = []
  text = text.replace(/`([^`]+)`/gim, (_m, code) => {
    const idx = inlineCodes.length
    inlineCodes.push(`<code>${code}</code>`)
    return `@@INLINECODE_${idx}@@`
  })

  // Links（label/url 均已 escape；href 需二次 sanitize）
  text = text.replace(/\[([^\]]+)\]\(([^)]+)\)/gim, (_m, label, url) => {
    const safe = sanitizeUrl(url)
    if (!safe) return label
    return `<a href="${safe}" target="_blank" rel="noopener noreferrer">${label}</a>`
  })

  // Bold / Italic（尽量避免贪婪跨行）
  text = text.replace(/\*\*([^*]+)\*\*/gim, '<b>$1</b>')
  text = text.replace(/\*([^*]+)\*/gim, '<i>$1</i>')

  // Restore inline codes
  text = text.replace(/@@INLINECODE_(\d+)@@/gim, (_m, idx) => inlineCodes[Number(idx)] || '')
  return text
}

function isCodePlaceholder(line) {
  return /^@@CODEBLOCK_\d+@@$/.test(String(line || '').trim())
}

const rendered = computed(() => {
  let raw = String(props.content || '')

  // 代码块先提取，避免后续处理破坏 <pre> 内部格式。
  const codeBlocks = []
  raw = raw.replace(/```([\s\S]*?)```/gim, (_m, code) => {
    const escaped = escapeHtml(code)
    const idx = codeBlocks.length
    codeBlocks.push(`<pre><code>${escaped}</code></pre>`)
    // 保证占位符独立成行，避免 <pre> 被包进 <p> 或 <li> 里。
    return `\n@@CODEBLOCK_${idx}@@\n`
  })

  // 先整体转义（再逐步放行/生成可控标签）。
  const text = escapeHtml(raw).replace(/\r\n?/g, '\n')
  const lines = text.split('\n')

  const blocks = []
  let paragraph = []

  function flushParagraph() {
    if (paragraph.length === 0) return
    const body = renderInline(paragraph.join('\n')).replace(/\n/g, '<br />')
    blocks.push(`<p>${body}</p>`)
    paragraph = []
  }

  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    const trimmed = String(line || '').trim()

    if (!trimmed) {
      flushParagraph()
      i += 1
      continue
    }

    if (isCodePlaceholder(trimmed)) {
      flushParagraph()
      blocks.push(trimmed)
      i += 1
      continue
    }

    if (trimmed.startsWith('### ')) {
      flushParagraph()
      blocks.push(`<h3>${renderInline(trimmed.slice(4))}</h3>`)
      i += 1
      continue
    }
    if (trimmed.startsWith('## ')) {
      flushParagraph()
      blocks.push(`<h2>${renderInline(trimmed.slice(3))}</h2>`)
      i += 1
      continue
    }
    if (trimmed.startsWith('# ')) {
      flushParagraph()
      blocks.push(`<h1>${renderInline(trimmed.slice(2))}</h1>`)
      i += 1
      continue
    }

    // Blockquote: 由于已整体 escape，这里匹配 &gt; 前缀。
    if (trimmed.startsWith('&gt; ')) {
      flushParagraph()
      const quoteLines = []
      while (i < lines.length) {
        const l = String(lines[i] || '')
        const t = l.trim()
        if (!t) break
        if (!t.startsWith('&gt; ')) break
        quoteLines.push(t.slice(5))
        i += 1
      }
      const quoteBody = renderInline(quoteLines.join('\n')).replace(/\n/g, '<br />')
      blocks.push(`<blockquote><p>${quoteBody}</p></blockquote>`)
      continue
    }

    // Unordered list
    if (/^\s*-\s+/.test(trimmed)) {
      flushParagraph()
      const items = []
      while (i < lines.length) {
        const l = String(lines[i] || '')
        const t = l.trim()
        if (!t) break
        if (!/^\s*-\s+/.test(t)) break
        items.push(t.replace(/^\s*-\s+/, ''))
        i += 1
      }
      const listBody = items.map((it) => `<li>${renderInline(it)}</li>`).join('')
      blocks.push(`<ul>${listBody}</ul>`)
      continue
    }

    paragraph.push(trimmed)
    i += 1
  }

  flushParagraph()

  const html = blocks.join('\n')
  return html.replace(/@@CODEBLOCK_(\d+)@@/gim, (_m, idx) => codeBlocks[Number(idx)] || '')
})
</script>

<style scoped>
.markdown-body {
  font-size: var(--text-md);
  line-height: var(--line-loose);
  color: var(--text-1);
}

.markdown-body.compact {
  font-size: var(--text-sm);
  line-height: var(--line-normal);
}

:deep(h1), :deep(h2), :deep(h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 700;
  line-height: 1.3;
}
:deep(h1) { font-size: 1.8em; }
:deep(h2) { font-size: 1.5em; border-bottom: 1px solid var(--border); padding-bottom: 0.3em; }
:deep(h3) { font-size: 1.25em; }

:deep(p) { margin: 0 0 1em 0; }
:deep(ul) { margin-bottom: 1em; padding-left: 20px; }
:deep(li) { margin-bottom: 0.25em; list-style-type: disc; }

:deep(blockquote) {
  border-left: 4px solid var(--border);
  padding-left: 16px;
  color: var(--text-2);
  margin: 1em 0;
}

:deep(pre) {
  background: var(--surface-2);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--border);
  overflow-x: auto;
  margin: 1em 0;
}
:deep(code) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
  background: var(--hover-bg);
  padding: 2px 4px;
  border-radius: 4px;
}
:deep(pre) :deep(code) {
  background: none;
  padding: 0;
}

:deep(a) {
  color: var(--accent);
  text-decoration: none;
}
:deep(a):hover {
  text-decoration: underline;
}
</style>
