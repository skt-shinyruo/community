// IM realtime 入站帧归一 seam：所有 v1 WebSocket frame 在这里完成版本闸门、字段校验
// 与语义归一，client 只分发归一后的语义事件，malformed frame 统一走 protocolError，
// 业务消费方（App / 会话详情 workflow）不再接触 raw wire fields。
import { isUuid } from '../utils/opaqueId'

export const IM_SCHEMA_VERSION = 1
const SEND_COMMANDS = new Set(['sendPrivateText', 'sendRoomText'])

// roomUpdatedBatch 线上当前不携带 schemaVersion（im-realtime RoomUpdateCoalescer 未写出版本），
// 为让房间更新保持可用，该帧类型容忍版本缺失或 1；其余已知帧严格执行版本闸门。
const VERSION_OPTIONAL_TYPES = new Set(['roomUpdatedBatch'])

function isPlainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isString(value) {
  return typeof value === 'string'
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim() !== ''
}

function isNullableString(value) {
  return value === null || typeof value === 'string'
}

function isPositiveSafeInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function isPositiveFiniteNumber(value) {
  return Number.isFinite(value) && value > 0
}

function firstInvalidField(msg, checks) {
  for (const [field, isValid] of checks) {
    if (!isValid(msg[field])) return field
  }
  return ''
}

function frame(framePayload) {
  return { status: 'frame', frame: framePayload }
}

function ignored() {
  return { status: 'ignored' }
}

function invalidFrame(frameType, field, closeCode = 0) {
  const error = { reasonCode: 'invalid_frame', frameType }
  if (field) error.field = field
  // 数据帧的字段级 malformed 不关闭连接：单帧错误不应拖垮仍可用的会话（与服务端语义一致）。
  // 握手帧（connected / connect reject）malformed 必须关闭：开着 socket 不发认证，
  // 而重连只由 onclose 触发，不关连接客户端会永远卡在「认证中」。
  return { status: 'error', error, closeCode }
}

function unsupportedSchemaVersion(frameType) {
  return {
    status: 'error',
    error: { reasonCode: 'unsupported_schema_version', frameType },
    closeCode: 1002
  }
}

function normalizeConnected(msg) {
  const field = firstInvalidField(msg, [
    ['sessionId', isNonEmptyString]
  ])
  if (field) return invalidFrame('connected', field, 1002)
  return frame({ type: 'connected', sessionId: msg.sessionId.trim() })
}

function normalizePrivateMessage(msg) {
  const field = firstInvalidField(msg, [
    ['conversationId', isNonEmptyString],
    ['seq', isPositiveSafeInteger],
    ['messageId', isUuid],
    ['fromUserId', isUuid],
    ['toUserId', isUuid],
    ['content', isString],
    ['createdAtEpochMillis', isPositiveFiniteNumber]
  ])
  if (field) return invalidFrame('privateMessage', field)
  return frame({
    type: 'privateMessage',
    conversationId: msg.conversationId,
    seq: msg.seq,
    messageId: msg.messageId,
    fromUserId: msg.fromUserId,
    toUserId: msg.toUserId,
    content: msg.content,
    // WS 帧时间戳字段 createdAtEpochMillis 归一到 HTTP history 同名的 createdAtEpochMs，
    // 下游与历史页共用同一份消息映射。
    createdAtEpochMs: msg.createdAtEpochMillis
  })
}

function normalizeCommitted(msg) {
  const field = firstInvalidField(msg, [
    ['cmd', (value) => SEND_COMMANDS.has(value)],
    ['clientMsgId', isNonEmptyString],
    ['requestId', isString],
    ['conversationId', isNullableString],
    ['roomId', isNullableString],
    ['messageId', isUuid],
    ['seq', isPositiveSafeInteger]
  ])
  if (field) return invalidFrame('committed', field)
  return frame({
    type: 'sendCommitted',
    cmd: msg.cmd,
    clientMsgId: msg.clientMsgId,
    requestId: msg.requestId,
    conversationId: msg.conversationId || '',
    roomId: msg.roomId || '',
    messageId: msg.messageId,
    seq: msg.seq
  })
}

function normalizeReject(msg) {
  const field = firstInvalidField(msg, [
    ['cmd', isString],
    ['clientMsgId', isString],
    ['requestId', isString],
    ['code', Number.isInteger],
    ['reasonCode', isString],
    ['message', isString]
  ])
  if (field) return invalidFrame('reject', field, msg.cmd === 'connect' ? 1002 : 0)
  if (msg.cmd === 'connect') {
    return frame({
      type: 'connectRejected',
      code: msg.code,
      reasonCode: msg.reasonCode,
      message: msg.message
    })
  }
  // 非发送命令的 reject（protocol / ping 等）没有消费方，静默忽略。
  if (!SEND_COMMANDS.has(msg.cmd)) return ignored()
  return frame({
    type: 'sendRejected',
    cmd: msg.cmd,
    clientMsgId: msg.clientMsgId,
    requestId: msg.requestId,
    code: msg.code,
    reasonCode: msg.reasonCode,
    message: msg.message,
    traceId: typeof msg.traceId === 'string' ? msg.traceId : ''
  })
}

function normalizeRoomUpdatedBatch(msg) {
  const items = msg.items
  const isValidItem = (item) =>
    isPlainObject(item) && isUuid(item.roomId) && isPositiveSafeInteger(item.lastSeq)
  if (!Array.isArray(items) || !items.every(isValidItem)) {
    return invalidFrame('roomUpdatedBatch', 'items')
  }
  return frame({
    type: 'roomUpdatedBatch',
    items: items.map((item) => ({ roomId: item.roomId, lastSeq: item.lastSeq }))
  })
}

const FRAME_NORMALIZERS = {
  connected: normalizeConnected,
  privateMessage: normalizePrivateMessage,
  committed: normalizeCommitted,
  reject: normalizeReject,
  roomUpdatedBatch: normalizeRoomUpdatedBatch
}

/**
 * 归一一个已解析的入站帧。返回三种结果：
 * - `{ status: 'frame', frame }`：已知帧且校验通过，frame 为归一后的语义事件 payload。
 * - `{ status: 'ignored' }`：未知或无消费方的帧类型，前向兼容静默忽略。
 * - `{ status: 'error', error, closeCode }`：malformed；error 即 protocolError 事件 payload，
 *   closeCode 非 0 时调用方应按协议错误关闭连接。
 */
export function normalizeInboundFrame(msg) {
  if (!isPlainObject(msg)) return invalidFrame('', '')
  const frameType = typeof msg.type === 'string' ? msg.type : ''
  const version = msg.schemaVersion
  const versionOk = VERSION_OPTIONAL_TYPES.has(frameType)
    ? version === undefined || version === IM_SCHEMA_VERSION
    : version === IM_SCHEMA_VERSION
  if (!versionOk) return unsupportedSchemaVersion(frameType)
  const normalize = FRAME_NORMALIZERS[frameType]
  if (!normalize) return ignored()
  return normalize(msg)
}
