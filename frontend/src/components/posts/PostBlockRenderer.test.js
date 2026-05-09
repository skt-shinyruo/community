// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PostBlockRenderer from './PostBlockRenderer.vue'

describe('PostBlockRenderer', () => {
  it('renders text, media, file, and code blocks without markdown html injection', () => {
    const wrapper = mount(PostBlockRenderer, {
      props: {
        blocks: [
          { type: 'paragraph', text: 'hello <strong>world</strong>' },
          { type: 'image', caption: 'chart', media: { url: '/chart.png', downloadUrl: '/chart.png' } },
          { type: 'video', caption: 'demo', media: { videoState: 'PROCESSING', downloadUrl: '/demo.mp4' } },
          { type: 'file', displayName: 'report.pdf', media: { downloadUrl: '/report.pdf', contentLength: 2048 } },
          { type: 'code', text: 'const x = 1', language: 'js' }
        ]
      }
    })

    expect(wrapper.text()).toContain('hello <strong>world</strong>')
    expect(wrapper.html()).not.toContain('<strong>world</strong>')
    expect(wrapper.get('img').attributes('src')).toBe('/chart.png')
    expect(wrapper.text()).toContain('demo')
    expect(wrapper.text()).toContain('转码处理中')
    expect(wrapper.get('a[download]').attributes('href')).toBe('/demo.mp4')
    expect(wrapper.text()).toContain('report.pdf')
    expect(wrapper.text()).toContain('2 KB')
    expect(wrapper.get('code').text()).toBe('const x = 1')
  })

  it('uses video sources when they are available', () => {
    const wrapper = mount(PostBlockRenderer, {
      props: {
        blocks: [{
          type: 'video',
          caption: 'ready',
          media: {
            posterUrl: '/poster.jpg',
            sources: [{ url: '/video-720.mp4', contentType: 'video/mp4' }]
          }
        }]
      }
    })

    const video = wrapper.get('video')
    expect(video.attributes('poster')).toBe('/poster.jpg')
    expect(wrapper.get('source').attributes('src')).toBe('/video-720.mp4')
  })
})
