import { buildOptions } from '../config/options.js'
import { token } from '../lib/auth.js'
import { authenticatedParams } from '../lib/auth.js'
import { get, randomThinkTime } from '../lib/http.js'

export const options = buildOptions('stress', { exec: 'stress' })

export function stress() {
  get('/api/posts?page=0&size=20&order=latest')
  get('/api/categories')
  get('/api/tags/hot?limit=20')
  get('/api/search/posts?q=k6&page=0&size=10')

  const accessToken = token()
  if (accessToken) {
    const params = authenticatedParams(accessToken)
    get('/api/auth/me', params)
    get('/api/notices/unread-count', params)
    get('/api/wallet/summary', params)
  }

  randomThinkTime(50, 250)
}

export default stress
