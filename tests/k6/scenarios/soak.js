import { buildOptions } from '../config/options.js'
import { token } from '../lib/auth.js'
import { authenticatedParams } from '../lib/auth.js'
import { config } from '../lib/config.js'
import { get, randomThinkTime } from '../lib/http.js'

export const options = buildOptions('soak', { exec: 'soak' })

export function soak() {
  get(`/api/posts?page=0&size=${config.readSize}&order=latest`)
  get('/api/tags/hot?limit=20')
  get('/api/search/posts?q=k6&page=0&size=10')

  const accessToken = token()
  if (accessToken) {
    const params = authenticatedParams(accessToken)
    get('/api/notices/summary', params)
    get('/api/wallet/summary', params)
    get('/api/drive/entries', params)
    get('/api/im/unread/summary', params)
  }

  randomThinkTime(250, 1000)
}

export default soak
