import { buildOptions } from '../config/options.js'
import { config } from '../lib/config.js'
import { get, randomThinkTime } from '../lib/http.js'

export const options = buildOptions('hot-path', { exec: 'hotpath' })

function optionalGet(path) {
  if (path) {
    get(path)
  }
}

export function hotpath() {
  get(`/api/feed/global?size=${config.readSize}`)

  const boardId = __ENV.K6_BOARD_ID || ''
  if (boardId) {
    optionalGet(`/api/boards/${boardId}/feed?size=${config.readSize}`)
  }

  const postId = __ENV.K6_POST_ID || ''
  if (postId) {
    optionalGet(`/api/posts/${postId}`)
  }

  randomThinkTime()
}

export default hotpath
