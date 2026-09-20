export declare class Counter {
  constructor(name: string, options?: { isTime?: boolean })
  add(value: number, tags?: Record<string, string>): void
}

export declare class Gauge {
  constructor(name: string, options?: { isTime?: boolean })
  add(value: number, tags?: Record<string, string>): void
}

export declare class Rate {
  constructor(name: string, options?: { isTime?: boolean })
  add(value: boolean, tags?: Record<string, string>): void
}

export declare class Trend {
  constructor(name: string, isTime?: boolean)
  add(value: number, tags?: Record<string, string>): void
}
