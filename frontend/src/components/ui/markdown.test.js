import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('protects fenced code before escaping and block rendering', () => {
    const html = renderMarkdown('before\n\n```\n<div>**literal**</div>\n```\n\nafter')

    expect(html).toBe([
      '<p>before</p>',
      '<pre><code>\n&lt;div&gt;**literal**&lt;/div&gt;\n</code></pre>',
      '<p>after</p>'
    ].join('\n'))
  })

  it('protects inline code from later emphasis conversion', () => {
    expect(renderMarkdown('Use `<tag> **literal**` and **bold**.')).toBe(
      '<p>Use <code>&lt;tag&gt; **literal**</code> and <b>bold</b>.</p>'
    )
  })

  it('escapes raw HTML and rejects unsafe link protocols', () => {
    expect(renderMarkdown('<img src=x> [bad](javascript:alert(1)) [ok](https://example.com)')).toBe(
      '<p>&lt;img src=x&gt; bad) <a href="https://example.com" target="_blank" rel="noopener noreferrer">ok</a></p>'
    )
  })

  it('renders mixed headings, quotes, lists, links, and emphasis', () => {
    const html = renderMarkdown([
      '# Title',
      '> quoted **text**',
      '- one',
      '- [two](/posts/2)'
    ].join('\n'))

    expect(html).toBe([
      '<h1>Title</h1>',
      '<blockquote><p>quoted <b>text</b></p></blockquote>',
      '<ul><li>one</li><li><a href="/posts/2" target="_blank" rel="noopener noreferrer">two</a></li></ul>'
    ].join('\n'))
  })

  it('preserves text that resembles the former placeholders', () => {
    const html = renderMarkdown('@@CODEBLOCK_0@@\n@@INLINECODE_0@@\n\n`real code`')

    expect(html).toBe([
      '<p>@@CODEBLOCK_0@@<br />@@INLINECODE_0@@</p>',
      '<p><code>real code</code></p>'
    ].join('\n'))
  })
})
