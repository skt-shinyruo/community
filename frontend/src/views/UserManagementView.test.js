// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { adminSearchUser, adminUpdateUserRole } = vi.hoisted(() => ({
  adminSearchUser: vi.fn(),
  adminUpdateUserRole: vi.fn()
}))

vi.mock('../api/services/adminUserService', () => ({
  adminSearchUser,
  adminUpdateUserRole
}))

import UserManagementView from './UserManagementView.vue'
import { useAuthStore } from '../stores/auth'

let auth
let showToast

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore()
  auth.installSession({
    accessToken: 'admin-token',
    me: {
      userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      username: 'admin',
      authorities: ['ROLE_ADMIN']
    }
  })
  showToast = vi.fn()

  return mount(UserManagementView, {
    global: {
      plugins: [pinia],
      provide: {
        showToast
      },
      stubs: {
        UiCard: { props: ['flat'], template: '<section><slot /></section>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiSelect: {
          props: ['modelValue', 'options'],
          emits: ['update:modelValue'],
          template: '<select v-bind="$attrs" :value="modelValue" @change="$emit(\'update:modelValue\', Number($event.target.value))"><option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option></select>'
        },
        UiModalConfirm: {
          props: ['title', 'message', 'confirmText', 'confirmVariant'],
          emits: ['confirm', 'cancel'],
          template: '<div><h2>{{ title }}</h2><p>{{ message }}</p><button @click="$emit(\'cancel\')">取消</button><button @click="$emit(\'confirm\')">{{ confirmText }}</button></div>'
        }
      }
    }
  })
}

describe('UserManagementView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    adminSearchUser.mockResolvedValue({
      data: {
        id: '11111111-1111-7111-8111-111111111111',
        username: 'alice',
        email: 'alice@example.com',
        status: 1,
        type: 0
      },
      traceId: 'trace-search'
    })
    adminUpdateUserRole.mockResolvedValue({ traceId: 'trace-role-update' })
  })

  it('searches by user fields and submits a confirmed role change', async () => {
    const wrapper = mountView()

    await wrapper.get('input[name="user-search-id"]').setValue('11111111-1111-7111-8111-111111111111')
    await wrapper.findAll('button').find((button) => button.text() === '搜索').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('搜索用户')
    expect(wrapper.text()).toContain('用户信息')
    expect(adminSearchUser).toHaveBeenCalledWith({
      userId: '11111111-1111-7111-8111-111111111111',
      username: '',
      email: ''
    })
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('USER')
    expect(wrapper.text()).toContain('审计原因')
    expect(wrapper.text()).toContain('提升为 ADMIN 风险较高')

    await wrapper.get('select[name="user-next-role"]').setValue('1')
    await wrapper.get('input[name="user-role-reason"]').setValue('权限升级')
    await wrapper.findAll('button').find((button) => button.text() === '提交变更').trigger('click')
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '确认').trigger('click')
    await flushPromises()

    expect(adminUpdateUserRole).toHaveBeenCalledWith({
      targetUserId: '11111111-1111-7111-8111-111111111111',
      type: 1,
      reason: '权限升级',
      confirm: true
    })
  })

  it('lets the newer user search win when the earlier response arrives last', async () => {
    const stale = deferred()
    adminSearchUser
      .mockReset()
      .mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce({
        data: {
          id: '22222222-2222-7222-8222-222222222222',
          username: 'bob',
          email: 'bob@example.com',
          status: 1,
          type: 2
        },
        traceId: 'trace-bob'
      })
    const wrapper = mountView()

    await wrapper.get('input[name="user-search-username"]').setValue('alice')
    const firstRequest = wrapper.vm.onSearch()
    wrapper.vm.qUsername = 'bob'
    const secondRequest = wrapper.vm.onSearch()
    await secondRequest

    stale.resolve({
      data: {
        id: '11111111-1111-7111-8111-111111111111',
        username: 'alice',
        email: 'alice@example.com',
        status: 1,
        type: 0
      },
      traceId: 'trace-alice'
    })
    await firstRequest
    await nextTick()

    expect(wrapper.text()).toContain('bob')
    expect(wrapper.text()).not.toContain('alice@example.com')
    expect(wrapper.emitted('trace').flat()).toEqual(['trace-bob'])
  })

  it('does not commit a role update response after the current admin loses permission', async () => {
    const pendingUpdate = deferred()
    adminUpdateUserRole.mockReset().mockReturnValueOnce(pendingUpdate.promise)
    const wrapper = mountView()

    await wrapper.get('input[name="user-search-id"]').setValue('11111111-1111-7111-8111-111111111111')
    await wrapper.findAll('button').find((button) => button.text() === '搜索').trigger('click')
    await flushPromises()
    await wrapper.get('select[name="user-next-role"]').setValue('1')
    await wrapper.get('input[name="user-role-reason"]').setValue('权限升级')
    await wrapper.findAll('button').find((button) => button.text() === '提交变更').trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '确认').trigger('click')
    await nextTick()

    auth.setMe({
      userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      username: 'former-admin',
      authorities: ['ROLE_USER']
    })
    await nextTick()
    pendingUpdate.resolve({ traceId: 'stale-role-update' })
    await flushPromises()

    expect(wrapper.text()).not.toContain('角色已更新')
    expect(wrapper.text()).not.toContain('alice@example.com')
    expect(showToast).not.toHaveBeenCalled()
  })
})
