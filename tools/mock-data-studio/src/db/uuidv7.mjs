import { randomBytes } from 'node:crypto'

const UUID_V7_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u
const UUID_REGEX =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u
const MAX_TIMESTAMP = 0xffffffffffffn
const MAX_SEQUENCE = 0xfffn
const RAND_B_MASK = 0x3fffffffffffffffn
const RFC_4122_VARIANT = 0x8000000000000000n

let lastTimestamp = -1n
let sequence = 0n

function formatUuidFromHex(hex) {
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20)
  ].join('-')
}

function bytesToBigInt(bytes) {
  return BigInt(`0x${Buffer.from(bytes).toString('hex')}`)
}

export function generateUuidV7(nowMillis = Date.now()) {
  const now = BigInt(nowMillis)

  if (lastTimestamp < 0n || now > lastTimestamp) {
    lastTimestamp = now
    sequence = 0n
  } else if (sequence < MAX_SEQUENCE) {
    sequence += 1n
  } else {
    lastTimestamp += 1n
    sequence = 0n
  }

  const timestamp = lastTimestamp & MAX_TIMESTAMP
  const msb = (timestamp << 16n) | 0x7000n | sequence
  const lsb = RFC_4122_VARIANT | (bytesToBigInt(randomBytes(8)) & RAND_B_MASK)
  const hex = msb.toString(16).padStart(16, '0') + lsb.toString(16).padStart(16, '0')
  return formatUuidFromHex(hex)
}

export function normalizeUuid(value) {
  const normalized = String(value ?? '').trim().toLowerCase()

  if (!UUID_REGEX.test(normalized)) {
    throw new Error(`Invalid UUID: ${value}`)
  }

  return normalized
}

export function isUuidV7(value) {
  return UUID_V7_REGEX.test(String(value ?? '').trim().toLowerCase())
}

export function uuidToBuffer(value) {
  const normalized = normalizeUuid(value)
  return Buffer.from(normalized.replaceAll('-', ''), 'hex')
}

export function bufferToUuid(value) {
  if (value == null) {
    return null
  }

  if (typeof value === 'string') {
    return normalizeUuid(value)
  }

  const buffer = Buffer.isBuffer(value)
    ? value
    : value instanceof Uint8Array
      ? Buffer.from(value)
      : value?.type === 'Buffer' && Array.isArray(value?.data)
        ? Buffer.from(value.data)
        : null

  if (!buffer || buffer.length !== 16) {
    throw new Error('UUID binary value must be 16 bytes')
  }

  return formatUuidFromHex(buffer.toString('hex').toLowerCase())
}
