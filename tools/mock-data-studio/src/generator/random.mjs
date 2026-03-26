function xmur3(seed) {
  let hash = 1779033703 ^ seed.length

  for (let index = 0; index < seed.length; index += 1) {
    hash = Math.imul(hash ^ seed.charCodeAt(index), 3432918353)
    hash = (hash << 13) | (hash >>> 19)
  }

  return () => {
    hash = Math.imul(hash ^ (hash >>> 16), 2246822507)
    hash = Math.imul(hash ^ (hash >>> 13), 3266489909)
    return (hash ^= hash >>> 16) >>> 0
  }
}

function sfc32(a, b, c, d) {
  return () => {
    a >>>= 0
    b >>>= 0
    c >>>= 0
    d >>>= 0
    const value = (a + b + d) | 0
    d = (d + 1) | 0
    a = b ^ (b >>> 9)
    b = (c + (c << 3)) | 0
    c = (c << 21) | (c >>> 11)
    c = (c + value) | 0
    return (value >>> 0) / 4294967296
  }
}

function normalizeSeed(seed) {
  if (seed == null) {
    return 'mock-data-studio'
  }

  if (typeof seed === 'string') {
    return seed
  }

  return JSON.stringify(seed)
}

export function createSeededRandom(seed) {
  const seedFactory = xmur3(normalizeSeed(seed))
  const nextNumber = sfc32(seedFactory(), seedFactory(), seedFactory(), seedFactory())

  function number() {
    return nextNumber()
  }

  function integer(min, max) {
    if (!Number.isInteger(min) || !Number.isInteger(max) || max < min) {
      throw new Error('integer(min, max) requires integer bounds where max >= min')
    }

    return min + Math.floor(number() * (max - min + 1))
  }

  function pick(items) {
    if (!Array.isArray(items) || items.length === 0) {
      throw new Error('pick(items) requires a non-empty array')
    }

    return items[integer(0, items.length - 1)]
  }

  function chance(probability) {
    return number() < probability
  }

  function weightedIndex(weights) {
    if (!Array.isArray(weights) || weights.length === 0) {
      throw new Error('weightedIndex(weights) requires a non-empty array')
    }

    const total = weights.reduce((sum, weight) => sum + Math.max(0, weight), 0)
    if (total <= 0) {
      return integer(0, weights.length - 1)
    }

    let cursor = number() * total

    for (let index = 0; index < weights.length; index += 1) {
      cursor -= Math.max(0, weights[index])
      if (cursor <= 0) {
        return index
      }
    }

    return weights.length - 1
  }

  function shuffle(items) {
    const copy = [...items]

    for (let index = copy.length - 1; index > 0; index -= 1) {
      const swapIndex = integer(0, index)
      ;[copy[index], copy[swapIndex]] = [copy[swapIndex], copy[index]]
    }

    return copy
  }

  return {
    chance,
    integer,
    number,
    pick,
    shuffle,
    weightedIndex
  }
}
