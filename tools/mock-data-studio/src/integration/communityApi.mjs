function resolveCommunityBaseUrl(config) {
  return config?.communityBaseUrl ?? config?.upstreams?.communityAppBaseUrl
}

function formatResponseError(status, bodyText) {
  if (!bodyText) {
    return `community-app search reindex failed with status ${status}`
  }

  return `community-app search reindex failed with status ${status}: ${bodyText}`
}

export function createCommunityApi({ config, fetchImpl = globalThis.fetch } = {}) {
  if (!fetchImpl) {
    throw new Error('fetch implementation is required')
  }

  const communityBaseUrl = resolveCommunityBaseUrl(config)

  if (!communityBaseUrl) {
    throw new Error('community base URL is required')
  }

  return {
    async reindexSearch() {
      const response = await fetchImpl(`${communityBaseUrl}/api/ops/search/reindex`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' }
      })

      if (response.ok) {
        return
      }

      const bodyText = await response.text().catch(() => '')
      throw new Error(formatResponseError(response.status, bodyText))
    }
  }
}
