// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PostBlockEditor from './PostBlockEditor.vue'
import UiFileInput from '../ui/UiFileInput.vue'
import { preparePostMediaUpload } from '../../api/services/postMediaService'

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
  beforeEach(() => {
    vi.clearAllMocks()
    preparePostMediaUpload.mockResolvedValue({
      data: {
        assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        uploadId: 'upload-1',
        upload: { url: '/upload', method: 'POST', fileField: 'file', fields: {}, headers: {} },
        constraints: { maxBytes: 10, mimeTypes: ['image/png'] }
      }
    })
  })

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

  it('emits completed media blocks with uploaded asset ids', async () => {
    const wrapper = mount(PostBlockEditor, {
      props: { modelValue: [{ type: 'image', assetId: '', caption: '', uploadState: 'idle' }] }
    })
    const file = new File(['image'], 'demo.png', { type: 'image/png' })

    await wrapper.getComponent(UiFileInput).vm.$emit('update:modelValue', file)
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({
      type: 'image',
      uploadState: 'completed',
      assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    })
  })

  it('removes text and code blocks with contextual controls', async () => {
    const wrapper = mount(PostBlockEditor, {
      props: {
        modelValue: [
          { type: 'paragraph', text: 'first' },
          { type: 'code', text: 'const x = 1', language: 'js' }
        ]
      }
    })

    await wrapper.get('[aria-label="移除代码块 2"]').trigger('click')

    let emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({ type: 'paragraph', text: 'first' })

    await wrapper.setProps({ modelValue: emitted })
    await wrapper.get('[aria-label="移除段落块 1"]').trigger('click')

    emitted = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({ type: 'paragraph', text: '' })
  })

  it('preserves stable client ids when blocks are updated and removed', async () => {
    const wrapper = mount(PostBlockEditor, {
      props: {
        modelValue: [
          { type: 'paragraph', text: 'first' },
          { type: 'image', assetId: '', caption: '', uploadState: 'idle' },
          { type: 'code', text: 'second', language: '' }
        ]
      }
    })

    const initialIds = wrapper.vm.$.setupState.blocks.map((block) => block.clientId)
    expect(new Set(initialIds).size).toBe(3)

    await wrapper.get('[data-test="block-text-0"]').setValue('updated')
    const afterUpdate = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(afterUpdate.map((block) => block.clientId)).toEqual(initialIds)

    await wrapper.setProps({ modelValue: afterUpdate })
    await wrapper.get('[aria-label="移除图片块 2"]').trigger('click')
    const afterRemove = wrapper.emitted('update:modelValue').at(-1)[0]
    expect(afterRemove.map((block) => block.clientId)).toEqual([initialIds[0], initialIds[2]])
  })
})
