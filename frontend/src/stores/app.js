// 应用级状态：traceId、全局提示等。

import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    traceId: ''
  }),
  actions: {
    setTraceId(traceId) {
      this.traceId = traceId || ''
    }
  }
})
