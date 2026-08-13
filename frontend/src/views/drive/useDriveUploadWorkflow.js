// @ts-check
import { reactive, ref } from 'vue'
import { createDriveUploadSession, uploadDriveFile } from '../../api/services/driveService'

export function useDriveUploadWorkflow({ workspace, runAction, cancelAction, reloadPage, setStatus }) {
  const progress = ref(/** @type {number | null} */ (null))
  /** @type {AbortController | null} */
  let uploadController = null

  async function handleSelection(event) {
    const files = Array.from(event?.target?.files || [])
    if (files.length === 0) return
    const targetParentId = workspace.currentFolderId.value
    const controller = new AbortController()
    uploadController = controller
    progress.value = 0
    try {
      await runAction('upload', async (request) => {
        for (const file of files) {
          const session = await createDriveUploadSession({
            parentId: targetParentId,
            file,
            signal: controller.signal
          })
          if (!request.isCurrent()) return
          await uploadDriveFile({
            session: session.data,
            file,
            signal: controller.signal,
            onProgress: ({ percent }) => {
              if (request.isCurrent() && percent != null) progress.value = percent
            }
          })
          if (!request.isCurrent()) return
          progress.value = 0
        }
        setStatus(`已上传 ${files.length} 个文件`)
        await reloadPage()
      })
    } catch {
      // The shared action runner owns the visible error for non-cancelled uploads.
    } finally {
      if (uploadController === controller) {
        uploadController = null
        progress.value = null
      }
    }
    if (event?.target) event.target.value = ''
  }

  function cancel() {
    if (!uploadController) return
    uploadController.abort()
    uploadController = null
    progress.value = null
    cancelAction('上传已取消')
  }

  function invalidate() {
    uploadController?.abort()
    uploadController = null
    progress.value = null
  }

  return {
    model: reactive({ progress, handleSelection, cancel }),
    invalidate,
    reset: invalidate
  }
}
