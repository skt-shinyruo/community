// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import EditContentModal from './EditContentModal.vue'

const PostBlockEditorStub = {
  name: 'PostBlockEditor',
  props: ['modelValue', 'disabled'],
  emits: ['update:modelValue'],
  template: '<div data-test="post-block-editor"></div>'
}

describe('EditContentModal', () => {
  it('exposes dialog semantics and closes with Escape', async () => {
    const wrapper = mount(EditContentModal, {
      props: {
        mode: 'comment',
        initialContent: 'comment'
      }
    })

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-labelledby')).toBeTruthy()

    await wrapper.get('.modal-mask').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('blocks post save while media upload is still pending', async () => {
    const wrapper = mount(EditContentModal, {
      props: {
        mode: 'post',
        initialTitle: 'title',
        initialBlocks: [
          { type: 'image', assetId: '', caption: 'caption', uploadState: 'uploading' }
        ]
      },
      global: {
        stubs: {
          PostBlockEditor: PostBlockEditorStub
        }
      }
    })

    await wrapper.get('button.btn:not(.secondary)').trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('媒体仍在上传，请等待上传完成后再保存')
  })

  it('ignores empty idle media blocks while saving post edits', async () => {
    const wrapper = mount(EditContentModal, {
      props: {
        mode: 'post',
        initialTitle: 'title',
        initialBlocks: [
          { type: 'paragraph', text: 'body' },
          { type: 'image', assetId: '', caption: '', uploadState: 'idle' }
        ]
      },
      global: {
        stubs: {
          PostBlockEditor: PostBlockEditorStub
        }
      }
    })

    await wrapper.get('button.btn:not(.secondary)').trigger('click')

    expect(wrapper.emitted('submit')).toHaveLength(1)
    expect(wrapper.emitted('submit')[0][0].blocks).toEqual([{ type: 'paragraph', text: 'body' }])
  })

  it('blocks post save when media upload failed', async () => {
    const wrapper = mount(EditContentModal, {
      props: {
        mode: 'post',
        initialTitle: 'title',
        initialBlocks: [
          { type: 'paragraph', text: 'body' },
          { type: 'video', assetId: '', caption: 'caption', uploadState: 'failed' }
        ]
      },
      global: {
        stubs: {
          PostBlockEditor: PostBlockEditorStub
        }
      }
    })

    await wrapper.get('button.btn:not(.secondary)').trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('媒体上传失败，请重试或移除后再保存')
  })

  it('submits clean block payload for completed post media', async () => {
    const wrapper = mount(EditContentModal, {
      props: {
        mode: 'post',
        initialTitle: 'title',
        initialBlocks: [
          {
            type: 'image',
            assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
            caption: 'caption',
            uploadState: 'completed',
            clientId: 'local-image'
          }
        ]
      },
      global: {
        stubs: {
          PostBlockEditor: PostBlockEditorStub
        }
      }
    })

    await wrapper.get('button.btn:not(.secondary)').trigger('click')

    expect(wrapper.emitted('submit')).toHaveLength(1)
    expect(wrapper.emitted('submit')[0][0]).toEqual({
      title: 'title',
      content: '',
      blocks: [
        {
          type: 'image',
          assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          caption: 'caption'
        }
      ]
    })
  })

  it('keeps comment editing as plain content', async () => {
    const wrapper = mount(EditContentModal, {
      props: {
        mode: 'comment',
        initialContent: 'comment'
      }
    })

    await wrapper.get('button.btn:not(.secondary)').trigger('click')

    expect(wrapper.emitted('submit')[0][0]).toMatchObject({ content: 'comment' })
  })
})
