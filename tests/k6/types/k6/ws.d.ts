export interface Socket {
  on(event: 'open' | 'message' | 'close' | 'error', handler: (...args: never[]) => void): void
  send(data: string): void
  close(): void
  setInterval(handler: () => void, intervalMs: number): number
  setTimeout(handler: () => void, timeoutMs: number): number
}

interface ConnectOptions {
  tags?: Record<string, string>
}
export declare const ws: {
  connect(url: string, params: ConnectOptions, handler: (socket: Socket) => void): { status: number }
}

export default ws
