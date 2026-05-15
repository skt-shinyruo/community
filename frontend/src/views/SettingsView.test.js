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
      objectId: '00000000-0000-7000-8000-000000000050',
      versionId: '00000000-0000-7000-8000-000000000051',
      upload: {
        url: '/api/oss/objects/00000000-0000-7000-8000-000000000050/complete',
        method: 'POST',
        fileField: 'file',
        fields: {
          sessionId: 'session-1',
          versionId: '00000000-0000-7000-8000-000000000051'
        },
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
      if (url === '/api/oss/objects/00000000-0000-7000-8000-000000000050/complete') {
        return Promise.resolve(okResult({ objectId: '00000000-0000-7000-8000-000000000050' }, 'trace-upload'))
      }
      return Promise.resolve(okResult({}, 'trace-post'))
    })
    http.put.mockResolvedValue(okResult({}, 'trace-update'))
  })

  it('uses the shared file input and keeps upload disabled until a file is selected', async () => {
    const wrapper = mountView()

    const uploadButton = findUiButton(wrapper, '上传并保存')
    const fileInput = wrapper.getComponent(UiFileInput)
    const file = new File(['avatar'], 'picked-avatar.png', { type: 'image/png' })

    expect(uploadButton.get('button').attributes('disabled')).toBeDefined()
    expect(fileInput.exists()).toBe(true)
    expect(wrapper.text()).not.toContain('OSS 服务')
    expect(wrapper.text()).not.toContain('Cloudflare R2')
    expect(wrapper.text()).not.toContain('本地文件')
    expect(wrapper.text()).not.toContain('排行榜')

    await fileInput.vm.$emit('update:modelValue', file)
    await nextTick()

    expect(uploadButton.get('button').attributes('disabled')).toBeUndefined()
    expect(fileInput.text()).toContain('picked-avatar.png')
  })

  it('passes the selected File through the existing upload flow', async () => {
    const wrapper = mountView()

    const file = new File(['avatar'], 'picked-avatar.png', { type: 'image/png' })
    const fileInput = wrapper.getComponent(UiFileInput)

    await fileInput.vm.$emit('update:modelValue', file)
    await nextTick()
    await findUiButton(wrapper, '上传并保存').trigger('click')
    await flushPromises()

    expect(http.post).toHaveBeenCalledWith('/api/users/7/avatar/upload-sessions', {
      fileName: 'picked-avatar.png',
      contentType: 'image/png',
      contentLength: 6,
      checksumSha256: ''
    })
    expect(http.post).toHaveBeenCalledWith('/api/oss/objects/00000000-0000-7000-8000-000000000050/complete', expect.any(FormData), {
      headers: {}
    })
    const form = http.post.mock.calls.find(([url]) => url === '/api/oss/objects/00000000-0000-7000-8000-000000000050/complete')[1]
    expect(form.get('file')).toBe(file)
    expect(form.get('sessionId')).toBe('session-1')
    expect(form.get('versionId')).toBe('00000000-0000-7000-8000-000000000051')
    expect(http.put).toHaveBeenCalledWith('/api/users/7/avatar', { objectId: '00000000-0000-7000-8000-000000000050' })
  })

  it('does not expose retired leaderboard copy', () => {
    const wrapper = mountView()

    expect(wrapper.text()).not.toContain('排行榜')
  })

})
