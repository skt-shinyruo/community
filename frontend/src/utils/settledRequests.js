export async function settleNamedRequests(requestFactories = {}) {
  const entries = Object.entries(requestFactories)
  const settled = await Promise.allSettled(entries.map(([, factory]) =>
    Promise.resolve().then(() => factory())
  ))
  const results = {}
  const succeededKeys = []
  const failedKeys = []

  settled.forEach((result, index) => {
    const key = entries[index][0]
    if (result.status === 'fulfilled') {
      results[key] = { ok: true, value: result.value, error: null }
      succeededKeys.push(key)
    } else {
      results[key] = { ok: false, value: undefined, error: result.reason }
      failedKeys.push(key)
    }
  })

  return {
    results,
    succeededKeys,
    failedKeys,
    anySucceeded: succeededKeys.length > 0,
    allSucceeded: failedKeys.length === 0
  }
}
