export const profiles = {
  smoke: {
    scenario: 'smoke',
    executor: 'constant-vus',
    vus: 2,
    duration: '1m',
    gracefulStop: '10s',
    tags: { profile: 'smoke' }
  },
  'api-mix': {
    scenario: 'api-mix',
    executor: 'ramping-arrival-rate',
    startRate: 5,
    timeUnit: '1s',
    preAllocatedVUs: 40,
    maxVUs: 120,
    stages: [
      { target: 20, duration: '2m' },
      { target: 60, duration: '5m' },
      { target: 60, duration: '10m' },
      { target: 10, duration: '2m' }
    ],
    gracefulStop: '30s',
    tags: { profile: 'api-mix' }
  },
  'write-paths': {
    scenario: 'write-paths',
    executor: 'ramping-arrival-rate',
    startRate: 1,
    timeUnit: '1s',
    preAllocatedVUs: 20,
    maxVUs: 80,
    stages: [
      { target: 3, duration: '2m' },
      { target: 8, duration: '5m' },
      { target: 8, duration: '10m' },
      { target: 1, duration: '2m' }
    ],
    gracefulStop: '45s',
    tags: { profile: 'write-paths' }
  },
  'im-ws': {
    scenario: 'im-ws',
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { target: 20, duration: '2m' },
      { target: 100, duration: '5m' },
      { target: 100, duration: '10m' },
      { target: 0, duration: '2m' }
    ],
    gracefulRampDown: '30s',
    tags: { profile: 'im-ws' }
  },
  soak: {
    scenario: 'soak',
    executor: 'ramping-arrival-rate',
    startRate: 5,
    timeUnit: '1s',
    preAllocatedVUs: 80,
    maxVUs: 200,
    stages: [
      { target: 40, duration: '5m' },
      { target: 40, duration: '2h' },
      { target: 5, duration: '5m' }
    ],
    gracefulStop: '1m',
    tags: { profile: 'soak' }
  },
  stress: {
    scenario: 'stress',
    executor: 'ramping-arrival-rate',
    startRate: 10,
    timeUnit: '1s',
    preAllocatedVUs: 120,
    maxVUs: 400,
    stages: [
      { target: 50, duration: '3m' },
      { target: 100, duration: '5m' },
      { target: 200, duration: '5m' },
      { target: 300, duration: '5m' },
      { target: 20, duration: '3m' }
    ],
    gracefulStop: '1m',
    tags: { profile: 'stress' }
  },
  spike: {
    scenario: 'spike',
    executor: 'ramping-arrival-rate',
    startRate: 5,
    timeUnit: '1s',
    preAllocatedVUs: 100,
    maxVUs: 500,
    stages: [
      { target: 20, duration: '1m' },
      { target: 250, duration: '30s' },
      { target: 250, duration: '2m' },
      { target: 20, duration: '1m' }
    ],
    gracefulStop: '45s',
    tags: { profile: 'spike' }
  }
}

export function profileFor(name) {
  return profiles[name] || profiles.smoke
}
