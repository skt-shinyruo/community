// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PostBlockEditor from './PostBlockEditor.vue'
import { preparePostMediaUpload, uploadPostMediaFile } from '../../api/services/postMediaService'

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

async function selectFile(wrapper, file) {
  const input = wrapper.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
}

/** 取最近一次 update:modelValue 事件的负载 */
function lastBlocks(wrapper) {
  const updates = wrapper.emitted('update:modelValue')
  const last = updates?.at(-1)
  if (!last) throw new Error('未发出 update:modelValue')
  return /** @type {Array<Record<string, unknown>>} */ (last[0])
}

describe('PostBlockEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // mocked 模块导入按真实签名收窄，这里需要完整的 Mock 能力
    const prepare = /** @type {import('vitest').Mock} */ (preparePostMediaUpload)
    const upload = /** @type {import('vitest').Mock} */ (uploadPostMediaFile)
    prepare.mockResolvedValue({
      data: {
        assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        uploadId: 'upload-1',
        upload: { url: '/upload', method: 'POST', fileField: 'file', fields: {}, headers: {} },
        constraints: { maxBytes: 10, mimeTypes: ['image/png'] }
      }
    })
    upload.mockResolvedValue({ traceId: 'trace-upload' })
  })

  it('emits paragraph blocks and can add code blocks', async () => {
    const wrapper = mount(PostBlockEditor, {
      props: { modelValue: [{ type: 'paragraph', text: '' }] }
    })

    await wrapper.get('[data-test="block-text-0"]').setValue('hello')
    await wrapper.get('[data-test="add-code-block"]').trigger('click')

    const emitted = lastBlocks(wrapper)
    expect(emitted[0]).toMatchObject({ type: 'paragraph', text: 'hello' })
    expect(emitted[1]).toMatchObject({ type: 'code' })
  })

  it('emits completed media blocks with uploaded asset ids', async () => {
    const wrapper = mount(PostBlockEditor, {
      props: { modelValue: [{ type: 'image', assetId: '', caption: '', uploadState: 'idle' }] }
    })
    const file = new File(['image'], 'demo.png', { type: 'image/png' })

    await selectFile(wrapper, file)
    await flushPromises()

    const emitted = lastBlocks(wrapper)
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({
      type: 'image',
      uploadState: 'completed',
      assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    })
    expect(preparePostMediaUpload).toHaveBeenCalledWith(expect.objectContaining({
      signal: expect.any(Object)
    }))
  })

  it('preserves and renders upload progress while the media request is pending', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolveUpload
    const upload = /** @type {import('vitest').Mock} */ (uploadPostMediaFile)
    upload.mockImplementation(({ onProgress }) => {
      onProgress({ loaded: 42, total: 100, percent: 42 })
      return new Promise((resolve) => {
        resolveUpload = resolve
      })
    })

    const wrapper = mount(PostBlockEditor, {
      props: { modelValue: [{ type: 'image', assetId: '', caption: '', uploadState: 'idle' }] }
    })
    const file = new File(['image'], 'demo.png', { type: 'image/png' })

    await selectFile(wrapper, file)
    await flushPromises()

    expect(wrapper.text()).toContain('上传中 42%')
    expect(lastBlocks(wrapper)[0]).toMatchObject({
      uploadState: 'uploading',
      uploadProgress: 42
    })

    if (!resolveUpload) throw new Error('未捕获 deferred resolve')
    resolveUpload({ traceId: 'trace-upload' })
    await flushPromises()
  })

  it('keeps media blocks failed when upload session has no asset id', async () => {
    ;(/** @type {import('vitest').Mock} */ (preparePostMediaUpload)).mockResolvedValue({
      data: {
        uploadId: 'upload-1',
        upload: { url: '/upload', method: 'POST', fileField: 'file', fields: {}, headers: {} },
        constraints: { maxBytes: 10, mimeTypes: ['image/png'] }
      }
    })
    const wrapper = mount(PostBlockEditor, {
      props: { modelValue: [{ type: 'image', assetId: '', caption: '', uploadState: 'idle' }] }
    })
    const file = new File(['image'], 'demo.png', { type: 'image/png' })

    await selectFile(wrapper, file)
    await flushPromises()

    const emitted = lastBlocks(wrapper)
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({
      type: 'image',
      uploadState: 'failed',
      assetId: ''
    })
    expect(wrapper.text()).toContain('上传失败')
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

    let emitted = lastBlocks(wrapper)
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({ type: 'paragraph', text: 'first' })

    await wrapper.setProps({ modelValue: emitted })
    await wrapper.get('[aria-label="移除段落块 1"]').trigger('click')

    emitted = lastBlocks(wrapper)
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

    const setupState = /** @type {Record<string, Array<Record<string, unknown>>>} */ (wrapper.vm.$.setupState)
    const initialIds = setupState.blocks.map((block) => block.clientId)
    expect(new Set(initialIds).size).toBe(3)

    await wrapper.get('[data-test="block-text-0"]').setValue('updated')
    const afterUpdate = lastBlocks(wrapper)
    expect(afterUpdate.map((block) => block.clientId)).toEqual(initialIds)

    await wrapper.setProps({ modelValue: afterUpdate })
    await wrapper.get('[aria-label="移除图片块 2"]').trigger('click')
    const afterRemove = lastBlocks(wrapper)
    expect(afterRemove.map((block) => block.clientId)).toEqual([initialIds[0], initialIds[2]])
  })
})
