import { describe, expect, it } from 'vitest'
import { emOnlyHtml } from './highlight'

describe('emOnlyHtml', () => {
  it('preserves exact <em>highlight</em> pairs', () => {
    expect(emOnlyHtml('<em>命中内容</em>')).toBe('<em>命中内容</em>')
    expect(emOnlyHtml('前缀<em>命中</em>后缀')).toBe('前缀<em>命中</em>后缀')
  })

  it('escapes script, event-handler attributes and ordinary HTML tags', () => {
    expect(emOnlyHtml('<script>alert(1)</script>')).toBe('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(emOnlyHtml('<img src=x onerror="alert(1)">')).toBe('&lt;img src=x onerror=&quot;alert(1)&quot;&gt;')
    expect(emOnlyHtml('<b>粗体</b><a href="/x">链接</a>')).toBe(
      '&lt;b&gt;粗体&lt;/b&gt;&lt;a href=&quot;/x&quot;&gt;链接&lt;/a&gt;'
    )
  })

  it('escapes <em> tags carrying attributes instead of whitelisting them', () => {
    expect(emOnlyHtml('<em class="hl">命中</em>')).toBe('&lt;em class=&quot;hl&quot;&gt;命中&lt;/em&gt;')
    expect(emOnlyHtml('<em onclick="alert(1)">命中</em>')).toBe('&lt;em onclick=&quot;alert(1)&quot;&gt;命中&lt;/em&gt;')
  })

  it('escapes unpaired <em> and </em> instead of emitting a stray tag', () => {
    expect(emOnlyHtml('命中</em>')).toBe('命中&lt;/em&gt;')
    expect(emOnlyHtml('<em>命中')).toBe('&lt;em&gt;命中')
  })

  it('escapes ampersands and quotes in plain text', () => {
    expect(emOnlyHtml('A & B "引号"')).toBe('A &amp; B &quot;引号&quot;')
  })
})
