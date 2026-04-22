// taxonomy：分类与热门标签的轻量缓存（右侧面板/发帖/列表共用）。

import { defineStore } from 'pinia'
import { listCategories, listHotTags } from '../api/services/taxonomyService'
import { normalizeOpaqueId } from '../utils/opaqueId'

export const useTaxonomyStore = defineStore('taxonomy', {
  state: () => ({
    categories: [],
    hotTags: [],
    categoriesLoaded: false,
    hotTagsLoaded: false
  }),
  getters: {
    categoriesById: (s) => {
      const map = new Map()
      ;(Array.isArray(s.categories) ? s.categories : []).forEach((c) => {
        const id = normalizeOpaqueId(c?.id)
        if (id) map.set(id, c)
      })
      return map
    }
  },
  actions: {
    async ensureCategories(force = false) {
      if (this.categoriesLoaded && !force) return
      try {
        const resp = await listCategories()
        this.categories = Array.isArray(resp?.data) ? resp.data : []
        this.categoriesLoaded = true
      } catch {
        // ignore：由调用方决定是否提示
      }
    },

    async ensureHotTags(limit = 8, force = false) {
      if (this.hotTagsLoaded && !force) return
      try {
        const resp = await listHotTags({ limit })
        this.hotTags = Array.isArray(resp?.data) ? resp.data : []
        this.hotTagsLoaded = true
      } catch {
        // ignore
      }
    }
  }
})
