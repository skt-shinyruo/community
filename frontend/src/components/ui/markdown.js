export function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function sanitizeUrl(raw) {
  const url = String(raw || '').trim()
  if (!url) return ''
  const lower = url.toLowerCase()

  if (lower.startsWith('http://')) return url
  if (lower.startsWith('https://')) return url
  if (lower.startsWith('mailto:')) return url
  if (lower.startsWith('tel:')) return url
  if (url.startsWith('/') || url.startsWith('./') || url.startsWith('../') || url.startsWith('#')) return url
  return ''
}

function createPlaceholderStore(source) {
  let prefix = '\u0000markdown-placeholder-'
  while (String(source).includes(prefix)) prefix += '-'
  const values = new Map()

  return {
    stash(value) {
      const token = `${prefix}${values.size}\u0000`
      values.set(token, value)
      return token
    },
    has(token) {
      return values.has(token)
    },
    restore(text) {
      let restored = String(text || '')
      for (const [token, value] of values) restored = restored.replaceAll(token, value)
      return restored
    }
  }
}

function protectFencedCodeBlocks(source, placeholders) {
  return String(source || '').replace(/```([\s\S]*?)```/gim, (_match, code) => {
    const token = placeholders.stash(`<pre><code>${escapeHtml(code)}</code></pre>`)
    return `\n${token}\n`
  })
}

function escapeMarkdown(source) {
  return escapeHtml(source).replace(/\r\n?/g, '\n')
}

function renderInlineMarkdown(escapedText, placeholders) {
  let text = String(escapedText || '')
  text = text.replace(/`([^`]+)`/gim, (_match, code) =>
    placeholders.stash(`<code>${code}</code>`)
  )
  text = text.replace(/\[([^\]]+)\]\(([^)]+)\)/gim, (_match, label, url) => {
    const safe = sanitizeUrl(url)
    if (!safe) return label
    return `<a href="${safe}" target="_blank" rel="noopener noreferrer">${label}</a>`
  })
  text = text.replace(/\*\*([^*]+)\*\*/gim, '<b>$1</b>')
  return text.replace(/\*([^*]+)\*/gim, '<i>$1</i>')
}

function renderMarkdownBlocks(text, placeholders) {
  const lines = String(text || '').split('\n')
  const blocks = []
  let paragraph = []

  function flushParagraph() {
    if (paragraph.length === 0) return
    const body = renderInlineMarkdown(paragraph.join('\n'), placeholders).replace(/\n/g, '<br />')
    blocks.push(`<p>${body}</p>`)
    paragraph = []
  }

  let index = 0
  while (index < lines.length) {
    const trimmed = String(lines[index] || '').trim()

    if (!trimmed) {
      flushParagraph()
      index += 1
      continue
    }
    if (placeholders.has(trimmed)) {
      flushParagraph()
      blocks.push(trimmed)
      index += 1
      continue
    }
    if (trimmed.startsWith('### ')) {
      flushParagraph()
      blocks.push(`<h3>${renderInlineMarkdown(trimmed.slice(4), placeholders)}</h3>`)
      index += 1
      continue
    }
    if (trimmed.startsWith('## ')) {
      flushParagraph()
      blocks.push(`<h2>${renderInlineMarkdown(trimmed.slice(3), placeholders)}</h2>`)
      index += 1
      continue
    }
    if (trimmed.startsWith('# ')) {
      flushParagraph()
      blocks.push(`<h1>${renderInlineMarkdown(trimmed.slice(2), placeholders)}</h1>`)
      index += 1
      continue
    }
    if (trimmed.startsWith('&gt; ')) {
      flushParagraph()
      const quoteLines = []
      while (index < lines.length) {
        const quoteLine = String(lines[index] || '').trim()
        if (!quoteLine || !quoteLine.startsWith('&gt; ')) break
        quoteLines.push(quoteLine.slice(5))
        index += 1
      }
      const body = renderInlineMarkdown(quoteLines.join('\n'), placeholders).replace(/\n/g, '<br />')
      blocks.push(`<blockquote><p>${body}</p></blockquote>`)
      continue
    }
    if (/^\s*-\s+/.test(trimmed)) {
      flushParagraph()
      const items = []
      while (index < lines.length) {
        const item = String(lines[index] || '').trim()
        if (!item || !/^\s*-\s+/.test(item)) break
        items.push(item.replace(/^\s*-\s+/, ''))
        index += 1
      }
      blocks.push(`<ul>${items.map((item) => `<li>${renderInlineMarkdown(item, placeholders)}</li>`).join('')}</ul>`)
      continue
    }

    paragraph.push(trimmed)
    index += 1
  }

  flushParagraph()
  return blocks.join('\n')
}

export function renderMarkdown(content) {
  const source = String(content || '')
  const placeholders = createPlaceholderStore(source)
  const protectedSource = protectFencedCodeBlocks(source, placeholders)
  const escapedSource = escapeMarkdown(protectedSource)
  const renderedBlocks = renderMarkdownBlocks(escapedSource, placeholders)
  return placeholders.restore(renderedBlocks)
}
