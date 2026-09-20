// Ambient declarations for the k6 execution environment: module APIs this
// suite imports plus k6 execution globals. Intentionally minimal surface.
import type { Response } from './http'
import type { Socket } from './ws'

declare global {
  const __ENV: Record<string, string | undefined>
  const __VU: number
  const __ITER: number
}

export declare function check<T>(val: T, checks: Record<string, (data?: T) => unknown>): boolean
export declare function sleep(duration: number): void

export type { Response, Socket }
