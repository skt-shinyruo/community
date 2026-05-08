// @vitest-environment jsdom

import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../stores/auth'
import UiButton from '../components/ui/UiButton.vue'
import UiFileInput from '../components/ui/UiFileInput.vue'

vi.mock('../api/http', () => ({
  default: {
    defaults: { baseURL: '' },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn()
  }
}))

vi.mock('../api/services/authService', () => ({
  me: vi.fn().mockResolvedValue({
    data: {
      userId: 7,
      username: 'aaa',
      headerUrl: '/files/avatar-updated.png',
      authorities: []
    },
    traceId: 'trace-me'
  })
}))

import SettingsView from './SettingsView.vue'
import http from '../api/http'

function okResult(data, traceId = 'trace-ok') {
  return {
    data: {
      code: 0,
      message: '',
      data,
      traceId
    }
  }
}

describe('SettingsView', () => {
  function uploadSession(overrides = {}) {
    return {
      uploadId: 'session-1',
      fileKey: 'avatar-upload-key',
      upload: {
        url: '/api/users/7/avatar/upload',
        method: 'POST',
        fileField: 'file',
        fields: { fileKey: 'avatar-upload-key' },
        headers: {},
        ...(overrides.upload || {})
      },
      constraints: {
        maxBytes: 256000,
        mimeTypes: ['image/png', 'image/jpeg'],
        ...(overrides.constraints || {})
      },
      expiresAt: '2026-05-08T12:00:00Z',
      ...overrides
    }
  }

  function mountView() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.setAccessToken('token')
    auth.setMe({
      userId: 7,
      username: 'aaa',
      headerUrl: '/files/current-avatar.png',
      authorities: []
    })

    return mount(SettingsView, {
      global: {
        plugins: [pinia]
      }
    })
  }

  function findUiButton(wrapper, text) {
    const button = wrapper.findAllComponents(UiButton).find((candidate) => candidate.text().includes(text))
    if (!button) throw new Error(`Button not found: ${text}`)
    return button
  }

  beforeEach(() => {
    http.get.mockReset()
    http.post.mockReset()
    http.put.mockReset()

    http.post.mockImplementation((url) => {
      if (url === '/api/users/7/avatar/upload-sessions') {
        return Promise.resolve(okResult(uploadSession(), 'trace-session'))
      }
      if (url === '/api/users/7/avatar/upload') {
        return Promise.resolve(okResult({}, 'trace-upload'))
      }
      return Promise.resolve(okResult({}, 'trace-post'))
    })
    http.put.mockResolvedValue(okResult({}, 'trace-update'))
  })

  it('uses the shared file input and keeps upload disabled until a file is selected', async () => {
    const wrapper = mountView()

    await findUiButton(wrapper, '获取上传参数').trigger('click')
    await flushPromises()

    const uploadButton = findUiButton(wrapper, '上传并保存')
    const fileInput = wrapper.getComponent(UiFileInput)
    const file = new File(['avatar'], 'picked-avatar.png', { type: 'image/png' })

    expect(uploadButton.get('button').attributes('disabled')).toBeDefined()
    expect(fileInput.exists()).toBe(true)
    expect(wrapper.text()).not.toContain('OSS 服务')
    expect(wrapper.text()).not.toContain('Cloudflare R2')
    expect(wrapper.text()).not.toContain('本地文件')

    await fileInput.vm.$emit('update:modelValue', file)
    await nextTick()

    expect(uploadButton.get('button').attributes('disabled')).toBeUndefined()
    expect(fileInput.text()).toContain('picked-avatar.png')
  })

  it('passes the selected File through the existing upload flow', async () => {
    const wrapper = mountView()

    await findUiButton(wrapper, '获取上传参数').trigger('click')
    await flushPromises()

    const file = new File(['avatar'], 'picked-avatar.png', { type: 'image/png' })
    const fileInput = wrapper.getComponent(UiFileInput)

    await fileInput.vm.$emit('update:modelValue', file)
    await nextTick()
    await findUiButton(wrapper, '上传并保存').trigger('click')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/api/users/7/avatar/upload-sessions')
    expect(http.post).toHaveBeenCalledWith('/api/users/7/avatar/upload', expect.any(FormData), {
      headers: {}
    })
    const form = http.post.mock.calls.find(([url]) => url === '/api/users/7/avatar/upload')[1]
    expect(form.get('file')).toBe(file)
    expect(form.get('fileKey')).toBe('avatar-upload-key')
    expect(http.put).toHaveBeenCalledWith('/api/users/7/avatar', { fileKey: 'avatar-upload-key' })
  })

  it('ignores storage provider fields returned by old servers', async () => {
    http.post.mockResolvedValueOnce(okResult(uploadSession({
      provider: 'unrecognized-provider'
    }), 'trace-session'))
    http.post.mockResolvedValueOnce(okResult({}, 'trace-upload'))

    const wrapper = mountView()
    await findUiButton(wrapper, '获取上传参数').trigger('click')
    await flushPromises()

    const file = new File(['avatar'], 'picked-avatar.png', { type: 'image/png' })
    await wrapper.getComponent(UiFileInput).vm.$emit('update:modelValue', file)
    await nextTick()
    await findUiButton(wrapper, '上传并保存').trigger('click')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/api/users/7/avatar/upload', expect.any(FormData), {
      headers: {}
    })
    expect(wrapper.text()).not.toContain('未知存储策略')
  })
})
