// @ts-check
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { showToast } from '../../ui/toastService'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { normalizeOpaqueId } from '../../utils/opaqueId'
import { useDriveConfirmation } from './useDriveConfirmation'
import { useDriveEntryWorkflow } from './useDriveEntryWorkflow'
import { useDriveShareWorkflow } from './useDriveShareWorkflow'
import { useDriveUploadWorkflow } from './useDriveUploadWorkflow'
import { useDriveWorkspaceState } from './useDriveWorkspaceState'

export function useDrivePageState() {
  const auth = useAuthStore()
  const reloadTracker = createLatestRequestTracker()
  const actionTracker = createLatestRequestTracker()
  const workspaceState = useDriveWorkspaceState()
  const loading = ref(false)
  const busyAction = ref('')
  const error = ref('')
  const isBusy = computed(() => loading.value || busyAction.value !== '')

  const session = {
    capture: () => ({ tokenGeneration: auth.tokenGeneration, userId: normalizeOpaqueId(auth.userId) }),
    isCurrent: (scope) => auth.authed && auth.tokenGeneration === scope.tokenGeneration &&
      normalizeOpaqueId(auth.userId) === scope.userId
  }
  const isCurrent = (tracker, token, scope) => tracker.isCurrent(token) && session.isCurrent(scope)
  // 反馈渠道（规范 6.3）：结果可见的动作静默更新；结果不可见（复制链接、上传完成 / 取消）走 toast；
  // 加载失败留在 page.error 由列表区呈现（空列表 UiState 错态 / 有数据内联 alert）；
  // 不可定位的动作失败走 error toast，可定位的失败经 runAction 的 onError 落到对应区块。
  const notify = (message) => { if (message) showToast({ type: 'success', text: message }) }
  const notifyError = (message) => { if (message) showToast({ type: 'error', text: message }) }
  let entryWorkflow
  let uploadWorkflow
  let shareWorkflow

  /**
   * @param {string} label
   * @param {(request: { isCurrent: () => boolean }) => Promise<any> | any} fn
   * @param {{ onError?: (message: string) => void }} [options]
   */
  function runAction(label, fn, { onError } = {}) {
    const token = actionTracker.begin()
    const scope = session.capture()
    const request = { isCurrent: () => isCurrent(actionTracker, token, scope) }
    busyAction.value = label
    error.value = ''
    if (shareWorkflow) shareWorkflow.model.error = ''
    return Promise.resolve()
      .then(() => fn(request))
      .catch((cause) => {
        if (request.isCurrent()) {
          const message = cause?.message || '操作失败'
          if (onError) onError(message)
          else notifyError(message)
        }
        throw cause
      })
      .finally(() => {
        if (request.isCurrent()) busyAction.value = ''
      })
  }

  function cancelAction(message) {
    actionTracker.invalidate()
    busyAction.value = ''
    error.value = ''
    if (message) showToast({ type: 'info', text: message })
  }

  const { confirmation, confirm, closeConfirmation, runConfirmation } = useDriveConfirmation({ isBusy })

  async function reload() {
    const token = reloadTracker.begin()
    const scope = session.capture()
    const includeShares = workspaceState.mode.value === 'shares'
    shareWorkflow.invalidate()
    loading.value = true
    error.value = ''
    try {
      const [entryOutcome, shareOutcome] = await Promise.all([
        entryWorkflow.refresh(),
        includeShares
          ? shareWorkflow.refresh()
          : Promise.resolve({ stale: false, successCount: 0, failures: [] })
      ])
      if (!isCurrent(reloadTracker, token, scope) || entryOutcome.stale || shareOutcome.stale) return
      const failures = [...entryOutcome.failures, ...shareOutcome.failures]
      if (failures.length > 0) {
        const message = failures[0]?.message || '请稍后重试'
        error.value = entryOutcome.successCount + shareOutcome.successCount > 0
          ? `部分网盘数据加载失败：${message}`
          : (failures[0]?.message || '加载网盘失败')
      }
    } catch (cause) {
      if (isCurrent(reloadTracker, token, scope)) error.value = cause?.message || '加载网盘失败'
    } finally {
      if (isCurrent(reloadTracker, token, scope)) loading.value = false
    }
  }

  const workflowContext = { workspace: workspaceState, session, runAction, reloadPage: reload, confirm, notify }
  entryWorkflow = useDriveEntryWorkflow(workflowContext)
  uploadWorkflow = useDriveUploadWorkflow({ ...workflowContext, cancelAction })
  shareWorkflow = useDriveShareWorkflow(workflowContext)

  async function navigate(change) {
    if (isBusy.value || !change()) return
    await reload()
  }

  const workspace = reactive({
    mode: workspaceState.mode,
    selectedEntryId: workspaceState.selectedEntryId,
    searchKeyword: workspaceState.searchKeyword,
    renameDraft: workspaceState.renameDraft,
    currentFolderLabel: workspaceState.currentFolderLabel,
    breadcrumbItems: workspaceState.breadcrumbItems,
    visibleEntries: workspaceState.visibleEntries,
    selectedEntry: workspaceState.selectedEntry,
    select: workspaceState.selectEntry,
    switchMode: (mode) => navigate(() => workspaceState.switchMode(mode)),
    search: () => navigate(() => { workspaceState.beginSearch(); return true }),
    clearSearch: () => navigate(() => { workspaceState.searchKeyword.value = ''; return true }),
    enterFolder: (entry) => navigate(() => workspaceState.enterFolder(entry)),
    goBreadcrumb: (index) => navigate(() => workspaceState.goBreadcrumb(index))
  })

  function invalidateRequests() {
    reloadTracker.invalidate()
    actionTracker.invalidate()
    entryWorkflow.invalidate()
    uploadWorkflow.invalidate()
    shareWorkflow.invalidate()
    loading.value = false
    busyAction.value = ''
    closeConfirmation()
  }

  function resetOwnerState() {
    error.value = ''
    closeConfirmation()
    workspaceState.reset()
    entryWorkflow.reset()
    uploadWorkflow.reset()
    shareWorkflow.reset()
  }

  watch(
    () => [auth.tokenGeneration, normalizeOpaqueId(auth.userId), auth.authed],
    ([, userId, authed], previous = []) => {
      invalidateRequests()
      if (!previous.length || userId !== previous[1]) resetOwnerState()
      if (authed) reload()
      else resetOwnerState()
    },
    { immediate: true }
  )
  onBeforeUnmount(invalidateRequests)

  const page = reactive({ loading, busyAction, error, isBusy, reload })
  const entries = entryWorkflow.model
  const upload = uploadWorkflow.model
  const shares = shareWorkflow.model
  return { page, workspace, entries, upload, shares, confirmation, closeConfirmation, runConfirmation }
}
