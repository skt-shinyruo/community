// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
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

function mountView() {
  const showToast = vi.fn()

  return mount(UserManagementView, {
    global: {
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

    expect(adminSearchUser).toHaveBeenCalledWith({
      userId: '11111111-1111-7111-8111-111111111111',
      username: '',
      email: ''
    })
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('USER')

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
})
