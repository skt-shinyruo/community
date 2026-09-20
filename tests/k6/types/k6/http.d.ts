export interface Response {
  status: number
  json<T = unknown>(): T
  body?: string | null
  error?: string
  error_code?: number
  headers?: Record<string, string | string[] | undefined>
}

interface Params {
  headers?: Record<string, string>
  tags?: Record<string, string>
}

export declare const http: {
  get(url: string, params?: Params): Response
  post(url: string, body: string | ArrayBuffer | null | undefined, params?: Params): Response
  put(url: string, body: string | ArrayBuffer | null | undefined, params?: Params): Response
}

export default http
