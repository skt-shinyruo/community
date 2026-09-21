// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../../stores/auth'

const { apiMe, invalidateUserProfile, createAvatarUploadSession, executeUploadSession, updateAvatar } = vi.hoisted(() => ({
  apiMe: vi.fn(),
  invalidateUserProfile: vi.fn(),
  createAvatarUploadSession: vi.fn(),
  executeUploadSession: vi.fn(),
  updateAvatar: vi.fn()
}))

vi.mock('../../api/services/authService', () => ({
  me: apiMe
}))
vi.mock('../../api/services/userService', () => ({
  createAvatarUploadSession,
  invalidateUserProfile,
  updateAvatar
}))
vi.mock('../../api/uploadSession', async () => ({
  ...(await vi.importActual('../../api/uploadSession')),
  executeUploadSession
}))

import { identityScope } from '../../stores/identityScope'
import { useAvatarUploadWorkflow } from './useAvatarUploadWorkflow'

const FILE = () => new File(['avatar'], 'picked-avatar.png', { type: 'image/png' })

const SESSION = () => ({
  uploadId: 'session-1',
  objectId: '00000000-0000-7000-8000-000000000050',
  versionId: '00000000-0000-7000-8000-000000000051',
  upload: {
    url: '/api/oss/objects/00000000-0000-7000-8000-000000000050/complete',
    method: 'POST',
    fileField: 'file',
    fields: { sessionId: 'session-1' },
    headers: {}
  },
  constraints: { maxBytes: 256000, mimeTypes: ['image/png'] },
  expiresAt: '2026-05-08T12:00:00Z'
})

function deferred() {
  /** @type {((value: unknown) => void) | undefined} */
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  if (!resolve) throw new Error('deferred resolve not captured')
  return { promise, resolve }
}

function createSubject() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.installSession({ accessToken: 'token' })
  auth.setMe({ userId: 7, username: 'aaa', headerUrl: '/files/current-avatar.png', authorities: [] })

  const workflow = useAvatarUploadWorkflow()
  return { auth, workflow }
}

describe('useAvatarUploadWorkflow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createAvatarUploadSession.mockImplementation(async () => ({ session: SESSION() }))
    executeUploadSession.mockImplementation(async ({ onProgress }) => {
      onProgress?.({ percent: 42 })
      return { data: { objectId: '00000000-0000-7000-8000-000000000050' } }
    })
    updateAvatar.mockResolvedValue(undefined)
    apiMe.mockResolvedValue({
      data: { userId: 7, username: 'aaa', headerUrl: '/files/avatar-updated.png', authorities: [] },
      traceId: 'trace-me'
    })
  })

  it('runs create -> upload -> save and refreshes me on the auth store', async () => {
    const { auth, workflow } = createSubject()
    workflow.model.selectFile(FILE())

    await workflow.model.submit()
    await Promise.resolve()

    expect(createAvatarUploadSession).toHaveBeenCalledWith(expect.any(File), '7', expect.any(AbortSignal))
    expect(executeUploadSession).toHaveBeenCalledWith(expect.objectContaining({
      session: expect.objectContaining({ uploadId: 'session-1' }),
      file: expect.any(File),
      operation: 'Upload Avatar',
      signal: expect.any(AbortSignal)
    }))
    expect(updateAvatar).toHaveBeenCalledWith('00000000-0000-7000-8000-000000000050', '7')
    expect(invalidateUserProfile).toHaveBeenCalledWith('7')
    expect(auth.me?.headerUrl).toBe('/files/avatar-updated.png')
    expect(workflow.model.successMsg).toBe('头像已更新。')
    expect(workflow.model.error).toBe('')
    expect(workflow.model.session.objectId).toBe('00000000-0000-7000-8000-000000000050')
    expect(workflow.model.phase).toBe('idle')
  })

  it('exposes progress and the cancellable creating/uploading phases', async () => {
    const pendingUpload = deferred()
    executeUploadSession.mockImplementation(({ onProgress }) => {
      onProgress?.({ percent: 42 })
      return pendingUpload.promise
    })
    const { workflow } = createSubject()
    workflow.model.selectFile(FILE())

    const pending = workflow.model.submit()
    await vi.waitFor(() => expect(executeUploadSession).toHaveBeenCalledTimes(1))

    expect(workflow.model.progress).toBe(42)
    expect(workflow.model.phase).toBe('uploading')
    expect(workflow.model.loading).toBe(true)
    expect(workflow.model.canCancel).toBe(true)

    const signal = executeUploadSession.mock.calls[0][0].signal
    workflow.model.cancel()

    expect(signal.aborted).toBe(true)
    expect(workflow.model.error).toBe('上传已取消')
    expect(workflow.model.phase).toBe('idle')
    expect(workflow.model.loading).toBe(false)

    pendingUpload.resolve({ data: { objectId: 'ignored' } })
    await pending
    await vi.waitFor(() => expect(workflow.model.loading).toBe(false))
    expect(updateAvatar).not.toHaveBeenCalled()
  })

  it('clears file selection and preview state', async () => {
    const { workflow } = createSubject()
    workflow.model.selectFile(FILE())
    await workflow.model.submit()

    workflow.model.clearFile()

    expect(workflow.model.file).toBe(null)
    expect(workflow.model.session.objectId).toBe('')
  })

  it('surfaces transport failures on the error message', async () => {
    executeUploadSession.mockRejectedValue(new Error('上传失败'))
    const { workflow } = createSubject()
    workflow.model.selectFile(FILE())

    await workflow.model.submit()

    expect(workflow.model.error).toBe('上传失败')
    expect(workflow.model.loading).toBe(false)
    expect(workflow.model.phase).toBe('idle')
  })

  it('discards a stale upload after the identity scope changes', async () => {
    const pendingSession = deferred()
    createAvatarUploadSession.mockReturnValue(pendingSession.promise)
    const { auth, workflow } = createSubject()
    workflow.model.selectFile(FILE())

    const scopeBefore = identityScope(auth)
    const pending = workflow.model.submit()
    auth.installSession({
      accessToken: 'new-token',
      me: { userId: 8, username: 'bbb', headerUrl: '/files/new-user.png', authorities: [] }
    })
    expect(identityScope(auth)).not.toBe(scopeBefore)

    pendingSession.resolve({ session: SESSION() })
    await pending
    await Promise.resolve()

    expect(executeUploadSession).not.toHaveBeenCalled()
    expect(updateAvatar).not.toHaveBeenCalled()
    expect(workflow.model.loading).toBe(false)
    expect(workflow.model.error).toBe('')
  })

  it('still invalidates the profile and shows success when the me refresh fails', async () => {
    apiMe.mockRejectedValue(new Error('me unavailable'))
    const { workflow } = createSubject()
    workflow.model.selectFile(FILE())

    await workflow.model.submit()
    await Promise.resolve()

    expect(invalidateUserProfile).toHaveBeenCalledWith('7')
    expect(workflow.model.successMsg).toBe('头像已更新。')
    expect(workflow.model.error).toBe('')
  })

  it('aborts in-flight transport on invalidate', async () => {
    const pendingUpload = deferred()
    executeUploadSession.mockReturnValue(pendingUpload.promise)
    const { workflow } = createSubject()
    workflow.model.selectFile(FILE())

    const pending = workflow.model.submit()
    await vi.waitFor(() => expect(executeUploadSession).toHaveBeenCalledTimes(1))

    const signal = executeUploadSession.mock.calls[0][0].signal
    workflow.invalidate()

    expect(signal.aborted).toBe(true)
    pendingUpload.resolve({ data: { objectId: 'ignored' } })
    await pending
    await vi.waitFor(() => expect(workflow.model.loading).toBe(false))
    expect(workflow.model.error).toBe('')
  })

  it('rejects missing object id after upload', async () => {
    executeUploadSession.mockResolvedValue({ data: {} })
    createAvatarUploadSession.mockResolvedValue({ session: { ...SESSION(), objectId: '' } })
    const { workflow } = createSubject()
    workflow.model.selectFile(FILE())

    await workflow.model.submit()

    expect(workflow.model.error).toBe('头像对象缺失，请重新上传')
    expect(updateAvatar).not.toHaveBeenCalled()
  })

  it('ignores submit without a file or identity', async () => {
    const { workflow } = createSubject()
    await workflow.model.submit()
    expect(createAvatarUploadSession).not.toHaveBeenCalled()

    workflow.model.selectFile(FILE())
    workflow.model.clearFile()
    workflow.model.submit()
    expect(createAvatarUploadSession).toHaveBeenCalledTimes(0)
  })
})
