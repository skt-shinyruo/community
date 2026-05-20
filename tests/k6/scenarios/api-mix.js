import { buildOptions } from '../config/options.js'
import { token } from '../lib/auth.js'
import { authenticatedParams } from '../lib/auth.js'
import { config } from '../lib/config.js'
import { get, randomThinkTime } from '../lib/http.js'

export const options = buildOptions('api-mix', { exec: 'apimix' })

function publicReadFlow() {
  get('/actuator/health')
  get(`/api/posts?page=0&size=${config.readSize}&order=latest`)
  get('/api/categories')
  get('/api/tags/hot?limit=20')
  get('/api/tags/suggest?q=k&limit=10')
  get('/api/search/posts?q=k6&page=0&size=10')
  get('/api/market/listings?page=0&size=10')
}

function authenticatedReadFlow(accessToken) {
  const params = authenticatedParams(accessToken)
  get('/api/auth/me', params)
  get('/api/notices/summary', params)
  get('/api/notices/unread-count', params)
  get('/api/wallet/summary', params)
  get('/api/bookmarks?page=0&size=10', params)
  get('/api/drive/space', params)
  get('/api/drive/entries', params)
  get('/api/im/unread/summary', params)
  get('/api/im/conversations?page=0&size=20', params)
}

export function apimix() {
  publicReadFlow()

  const accessToken = token()
  if (accessToken) {
    authenticatedReadFlow(accessToken)
  }

  randomThinkTime()
}

export default apimix
