import { Router } from 'express'

function buildGenerateFormMetadata({ config } = {}) {
  return {
    modes: [
      {
        id: 'auto-fill',
        label: '自动补数'
      },
      {
        id: 'manual-generate',
        label: '手动生成'
      }
    ],
    defaultDraft: {
      mode: 'manual-generate',
      scenePresetId: 'community-seed',
      requestedBy: 'local-dev',
      counts: {
        users: 6,
        posts: 12,
        comments: 24,
        socialFollows: 16,
        socialLikes: 24
      },
      aiEnhancement: false,
      jobRequest: {
        requestedBy: 'local-dev',
        batchType: 'demo-seed',
        jobType: 'demo-seed',
        mode: 'manual-generate',
        aiEnhancement: false
      }
    },
    scenePresets: [
      {
        id: 'default-deficit',
        label: '默认批次缺口',
        mode: 'auto-fill',
        counts: {
          users: 0,
          posts: 0,
          comments: 0,
          socialFollows: 0,
          socialLikes: 0
        },
        aiEnhancement: false,
        jobRequest: {
          requestedBy: 'startup-auto-fill',
          batchType: 'startup-auto-fill',
          jobType: 'startup-auto-fill',
          mode: 'auto-fill',
          aiEnhancement: false
        }
      },
      {
        id: 'community-seed',
        label: '社区热启动',
        mode: 'manual-generate',
        counts: {
          users: 6,
          posts: 12,
          comments: 24,
          socialFollows: 16,
          socialLikes: 24
        },
        aiEnhancement: false,
        jobRequest: {
          requestedBy: 'local-dev',
          batchType: 'demo-seed',
          jobType: 'demo-seed',
          mode: 'manual-generate',
          aiEnhancement: false
        }
      }
    ],
    ai: {
      enabled: Boolean(config?.ai?.enabled),
      ready: Boolean(config?.ai?.ready),
      provider: config?.ai?.provider ?? 'openai',
      model: config?.ai?.model ?? null,
      maxItemsPerJob: config?.ai?.maxItemsPerJob ?? null
    }
  }
}

export function buildHealthRouter({ config } = {}) {
  const router = Router()
  const serviceName = config?.serviceName || 'mock-data-studio'

  router.get('/', (_req, res) => {
    res.json({
      ok: true,
      service: serviceName,
      ui: {
        title: 'Mock Data Studio',
        apiBasePath: '/api',
        generateForm: buildGenerateFormMetadata({ config })
      }
    })
  })

  return router
}
