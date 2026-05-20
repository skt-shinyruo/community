import { buildOptions } from '../config/options.js'
import { token } from '../lib/auth.js'
import { runImWebSocket } from '../lib/im.js'

export const options = buildOptions('im-ws', { exec: 'imws' })

export function imws() {
  const accessToken = token()
  if (accessToken) {
    runImWebSocket(accessToken)
  }
}

export default imws
