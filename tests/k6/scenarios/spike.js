import { buildOptions } from '../config/options.js'
import { get, randomThinkTime } from '../lib/http.js'

export const options = buildOptions('spike', { exec: 'spike' })

export function spike() {
  get('/actuator/health')
  get('/api/posts?page=0&size=20&order=latest')
  get('/api/tags/hot?limit=20')
  get('/api/search/posts?q=k6&page=0&size=10')
  randomThinkTime(20, 100)
}

export default spike
