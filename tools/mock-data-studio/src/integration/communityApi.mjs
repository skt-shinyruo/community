import { createHmac } from 'node:crypto'

function resolveCommunityBaseUrl(config) {
  return config?.communityBaseUrl ?? config?.upstreams?.communityAppBaseUrl
}

function encodeBase64UrlJson(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url')
}

function signHs256(unsignedToken, secret) {
  return createHmac('sha256', secret).update(unsignedToken).digest('base64url')
}

function buildAdminJwt(config, now = () => Math.floor(Date.now() / 1000)) {
  const secret = config?.reindexAuth?.jwtHmacSecret

  if (typeof secret !== 'string' || secret.trim() === '') {
    return null
  }

  const issuedAt = now()
  const expiresAt = issuedAt + Math.max(1, Number.parseInt(config?.reindexAuth?.jwtTtlSeconds ?? 120, 10) || 120)
  const header = {
    alg: 'HS256',
    typ: 'JWT'
  }
  const payload = {
    iss: config?.reindexAuth?.jwtIssuer ?? 'community-auth',
    sub: 'mock-data-studio',
    username: 'mock-data-studio',
    authorities: ['ROLE_ADMIN'],
    iat: issuedAt,
    exp: expiresAt
  }

  const encodedHeader = encodeBase64UrlJson(header)
  const encodedPayload = encodeBase64UrlJson(payload)
  const unsignedToken = `${encodedHeader}.${encodedPayload}`
  const signature = signHs256(unsignedToken, secret)
  return `${unsignedToken}.${signature}`
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
      const bearerToken = buildAdminJwt(config)
      const headers = {
        'content-type': 'application/json'
      }

      if (bearerToken) {
        headers.authorization = `Bearer ${bearerToken}`
      }

      const response = await fetchImpl(`${communityBaseUrl}/api/ops/search/reindex`, {
        method: 'POST',
        headers
      })

      if (response.ok) {
        return
      }

      const bodyText = await response.text().catch(() => '')
      throw new Error(formatResponseError(response.status, bodyText))
    }
  }
}
