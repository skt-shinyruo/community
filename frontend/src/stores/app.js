// 应用级状态：traceId、全局提示等。

import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    traceId: '',
    toast: { type: '', message: '' }
  }),
  actions: {
    setTraceId(traceId) {
      this.traceId = traceId || ''
    },
    toastInfo(message) {
      this.toast = { type: 'info', message: message || '' }
    },
    toastError(message) {
      this.toast = { type: 'error', message: message || '' }
    },
    clearToast() {
      this.toast = { type: '', message: '' }
    }
  }
})

