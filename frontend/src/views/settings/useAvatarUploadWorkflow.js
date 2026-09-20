// @ts-check
import { computed, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { identityScope } from '../../stores/identityScope'
import { me as apiMe } from '../../api/services/authService'
import { createAvatarUploadSession, invalidateUserProfile, updateAvatar } from '../../api/services/userService'
import { executeUploadSession, normalizeUploadSession } from '../../api/uploadSession'
import { normalizeOpaqueId } from '../../utils/opaqueId'

// Settings 头像上传会话 transport：创建会话 -> OSS 提交 -> 保存头像 -> 刷新 me，
// 竞态用 uploadGeneration + 身份作用域双重丢弃，取消经 AbortController 中止在途请求。
export function useAvatarUploadWorkflow() {
  const auth = useAuthStore()

  const loading = ref(false)
  const error = ref('')
  const successMsg = ref('')
  const session = reactive(normalizeUploadSession())

  const pickedFile = ref(null)
  const selectedPreviewUrl = ref('')
  const uploadProgress = ref(null)
  const uploadPhase = ref('idle')
  let uploadGeneration = 0
  /** @type {AbortController | null} */
  let uploadController = null

  const sessionScope = computed(() => identityScope(auth))
  const previewUrl = computed(() => selectedPreviewUrl.value)
  const canCancel = computed(() => loading.value && ['creating', 'uploading'].includes(uploadPhase.value))
  const actionText = computed(() => {
    if (!loading.value) return '上传并保存'
    if (uploadPhase.value === 'saving') return '保存中…'
    return uploadProgress.value == null ? '上传中…' : `上传中 ${uploadProgress.value}%`
  })

  function revokePreview() {
    if (selectedPreviewUrl.value && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(selectedPreviewUrl.value)
    }
  }

  function selectFile(file) {
    revokePreview()
    selectedPreviewUrl.value = ''
    Object.assign(session, normalizeUploadSession())
    pickedFile.value = file || null
    if (!file || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') return
    selectedPreviewUrl.value = URL.createObjectURL(file)
  }

  function clearFile() {
    revokePreview()
    selectedPreviewUrl.value = ''
    Object.assign(session, normalizeUploadSession())
    pickedFile.value = null
  }

  function isCurrentUpload(generation, scope) {
    return generation === uploadGeneration && scope === sessionScope.value
  }

  async function submit() {
    error.value = ''
    successMsg.value = ''
    const file = pickedFile.value
    const userId = normalizeOpaqueId(auth.userId)
    if (!file || !userId) return

    const generation = ++uploadGeneration
    const scope = sessionScope.value
    const controller = new AbortController()
    uploadController = controller
    uploadProgress.value = null
    uploadPhase.value = 'creating'
    loading.value = true
    try {
      const created = await createAvatarUploadSession(file, userId, controller.signal)
      if (!isCurrentUpload(generation, scope)) return
      Object.assign(session, created.session)

      uploadPhase.value = 'uploading'
      const { data } = await executeUploadSession({
        session: created.session,
        file,
        operation: 'Upload Avatar',
        signal: controller.signal,
        onProgress: ({ percent }) => {
          if (isCurrentUpload(generation, scope) && percent != null) uploadProgress.value = percent
        }
      })
      if (!isCurrentUpload(generation, scope)) return
      const objectId = String(data?.objectId || created.session.objectId || '').trim()
      if (!objectId) {
        throw new Error('头像对象缺失，请重新上传')
      }
      uploadPhase.value = 'saving'
      await updateAvatar(objectId, userId)
      if (!isCurrentUpload(generation, scope)) return
      invalidateUserProfile(userId)
      try {
        const { data } = await apiMe()
        if (!isCurrentUpload(generation, scope)) return
        auth.setMe(data)
      } catch {
        if (!isCurrentUpload(generation, scope)) return
        // ignore: 头像已更新，页面可通过刷新/重新进入触发 me 拉取。
      }
      if (!isCurrentUpload(generation, scope)) return
      successMsg.value = '头像已更新。'
    } catch (e) {
      if (!isCurrentUpload(generation, scope)) return
      error.value = e?.message || '更新失败'
    } finally {
      if (isCurrentUpload(generation, scope)) {
        loading.value = false
        uploadPhase.value = 'idle'
        uploadController = null
      }
    }
  }

  function haltTransport() {
    uploadGeneration += 1
    uploadController?.abort()
    uploadController = null
    uploadProgress.value = null
    uploadPhase.value = 'idle'
    loading.value = false
  }

  function cancel() {
    if (!canCancel.value) return
    haltTransport()
    error.value = '上传已取消'
  }

  // 生命周期兜底（卸载接线在视图层，与 useDriveUploadWorkflow 同形）：
  // 中止在途请求并释放预览 object URL。
  function invalidate() {
    haltTransport()
    revokePreview()
    selectedPreviewUrl.value = ''
  }

  // 身份切换（登出 / 换账号）整体失效：中止在途 transport、清空反馈与已选文件。
  watch(sessionScope, () => {
    haltTransport()
    error.value = ''
    successMsg.value = ''
    clearFile()
  })

  const model = reactive({
    file: pickedFile,
    previewUrl,
    session,
    loading,
    phase: uploadPhase,
    progress: uploadProgress,
    error,
    successMsg,
    canCancel,
    actionText,
    selectFile,
    clearFile,
    submit,
    cancel
  })

  return { model, invalidate }
}
