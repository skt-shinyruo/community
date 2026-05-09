// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PostBlockEditor from './PostBlockEditor.vue'

vi.mock('../../api/services/postMediaService', () => ({
  inferMediaKind: vi.fn(() => 'IMAGE'),
  preparePostMediaUpload: vi.fn().mockResolvedValue({
    data: {
      assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      uploadId: 'upload-1',
      upload: { url: '/upload', method: 'POST', fileField: 'file', fields: {}, headers: {} },
      constraints: { maxBytes: 10, mimeTypes: ['image/png'] }
    }
  }),
  uploadPostMediaFile: vi.fn().mockResolvedValue({ traceId: 'trace-upload' })
}))

describe('PostBlockEditor', () => {
  it('emits paragraph blocks and can add code blocks', async () => {
    const wrapper = mount(PostBlockEditor, {
      props: { modelValue: [{ type: 'paragraph', text: '' }] }
    })

    await wrapper.get('[data-test="block-text-0"]').setValue('hello')
    await wrapper.get('[data-test="add-code-block"]').trigger('click')

    const emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted[0]).toMatchObject({ type: 'paragraph', text: 'hello' })
    expect(emitted[1]).toMatchObject({ type: 'code' })
  })
})
