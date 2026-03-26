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

    http.get.mockResolvedValue(
      okResult({
        provider: 'local',
        fileName: 'avatar-upload.png',
        uploadUrl: '/api/upload/avatar',
        uploadMethod: 'POST',
        maxBytes: 256000,
        mimeLimit: 'image/*'
      }, 'trace-token')
    )
    http.post.mockResolvedValue(okResult({}, 'trace-upload'))
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

    expect(http.post).toHaveBeenCalledTimes(1)
    expect(http.post).toHaveBeenCalledWith('/api/upload/avatar', expect.any(FormData))
    const form = http.post.mock.calls[0][1]
    expect(form.get('file')).toBe(file)
    expect(form.get('fileName')).toBe('avatar-upload.png')
  })
})
