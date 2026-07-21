import axios from 'axios'

import { resolveApiBaseUrl } from '../config/endpointResolution'
import { unwrapResultBody } from '../api/result'

const refreshTransport = axios.create({
  baseURL: resolveApiBaseUrl(),
  withCredentials: true,
  timeout: 15000
})

export async function requestRefreshToken() {
  const response = await refreshTransport.post('/api/auth/refresh', null)
  return unwrapResultBody(response.data, '刷新登录状态')
}

export async function requestCurrentUser(accessToken) {
  const response = await refreshTransport.get('/api/auth/me', {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  })
  return unwrapResultBody(response.data, '获取用户信息')
}
