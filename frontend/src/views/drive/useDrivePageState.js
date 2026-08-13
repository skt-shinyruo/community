// @ts-check
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { createLatestRequestTracker } from '../../utils/latestRequest'
import { normalizeOpaqueId } from '../../utils/opaqueId'
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
  const statusMessage = ref('')
  const isBusy = computed(() => loading.value || busyAction.value !== '')

  const session = {
    capture: () => ({ tokenGeneration: auth.tokenGeneration, userId: normalizeOpaqueId(auth.userId) }),
    isCurrent: (scope) => auth.authed && auth.tokenGeneration === scope.tokenGeneration &&
      normalizeOpaqueId(auth.userId) === scope.userId
  }
  const isCurrent = (tracker, token, scope) => tracker.isCurrent(token) && session.isCurrent(scope)
  const setError = (message) => { error.value = message }
  const setStatus = (message) => { statusMessage.value = message }
  let entryWorkflow
  let uploadWorkflow
  let shareWorkflow

  function runAction(label, fn) {
    const token = actionTracker.begin()
    const scope = session.capture()
    const request = { isCurrent: () => isCurrent(actionTracker, token, scope) }
    busyAction.value = label
    error.value = ''
    statusMessage.value = ''
    if (shareWorkflow) shareWorkflow.model.error = ''
    return Promise.resolve()
      .then(() => fn(request))
      .catch((cause) => {
        if (request.isCurrent()) error.value = cause?.message || '操作失败'
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
    statusMessage.value = message
  }

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

  const workflowContext = { workspace: workspaceState, session, runAction, reloadPage: reload, setStatus }
  entryWorkflow = useDriveEntryWorkflow({ ...workflowContext, setError })
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
  }

  function resetOwnerState() {
    error.value = ''
    statusMessage.value = ''
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

  const page = reactive({ loading, busyAction, error, statusMessage, isBusy, reload })
  const entries = entryWorkflow.model
  const upload = uploadWorkflow.model
  const shares = shareWorkflow.model
  return { page, workspace, entries, upload, shares }
}
