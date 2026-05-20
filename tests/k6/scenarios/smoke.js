import { buildOptions } from '../config/options.js'
import { token } from '../lib/auth.js'
import { authenticatedParams } from '../lib/auth.js'
import { get, randomThinkTime } from '../lib/http.js'

export const options = buildOptions('smoke', { exec: 'smoke' })

export function smoke() {
  get('/actuator/health')
  get('/api/runtime-config')
  get('/api/categories')
  get('/api/tags/hot?limit=20')
  get('/api/posts?page=0&size=10&order=latest')

  const accessToken = token()
  if (accessToken) {
    get('/api/auth/me', authenticatedParams(accessToken))
    get('/api/notices/summary', authenticatedParams(accessToken))
  }

  randomThinkTime(100, 300)
}

export default smoke
