import { generateImPhaseDataset } from '../generator/domainGenerator.mjs'
import { appendEntityRefs, buildTimestampSource, createGeneratedRef, formatBulkInsert, toInsertParams } from './shared.mjs'

function buildEmptyInsertedCounts() {
  return {
    imRooms: 0,
    imRoomMembers: 0,
    imRoomMessages: 0,
    imConversations: 0,
    imPrivateMessages: 0
  }
}

async function loadExistingState(runDb) {
  const [users, conversations] = await Promise.all([
    runDb.query(`select id from user order by id asc`),
    runDb.query(`select conversation_id, user_a, user_b from im_core.im_conversation order by conversation_id asc`)
  ])

  return {
    users: users.map((user) => ({ id: Number(user.id) })),
    conversations: conversations.map((conversation) => ({
      conversationId: conversation.conversation_id,
      userA: Number(conversation.user_a),
      userB: Number(conversation.user_b)
    }))
  }
}

export function createImWriter({
  db,
  entityRefRepository,
  now = () => new Date().toISOString()
} = {}) {
  if (!db?.query || !db?.execute) {
    throw new Error('db.query and db.execute are required')
  }

  if (!entityRefRepository?.appendForBatch) {
    throw new Error('entityRefRepository.appendForBatch is required')
  }

  const nextTimestamp = buildTimestampSource(now, 'imWriter.timestamp')

  return {
    async writePhase({ batchId, plan, seed = null } = {}) {
      if (batchId == null) {
        throw new Error('batchId is required')
      }

      if (!plan) {
        throw new Error('plan is required')
      }

      const imPhaseNeedsWork = plan?.phases?.some((phase) => phase.name === 'im' && phase.needsWork)
      if (!imPhaseNeedsWork) {
        return {
          insertedCounts: buildEmptyInsertedCounts(),
          generatedRefs: []
        }
      }

      return db.withTransaction(async (runDb) => {
        const existing = await loadExistingState(runDb)
        const dataset = generateImPhaseDataset({
          plan,
          seed: seed == null ? null : `${seed}:im`,
          existing
        })

        const insertedCounts = buildEmptyInsertedCounts()
        const generatedRefs = []

        const roomRows = dataset.rooms.map((room) => {
          const timestamp = nextTimestamp()
          return [room.roomId, room.name, room.lastSeq, timestamp.mysql, timestamp.mysql]
        })
        if (roomRows.length > 0) {
          await runDb.execute(
            formatBulkInsert('im_core.im_room', ['room_id', 'name', 'last_seq', 'created_at', 'updated_at'], roomRows.length),
            toInsertParams(roomRows)
          )
        }
        roomRows.forEach(([roomId]) => {
          generatedRefs.push(createGeneratedRef('im_rooms', roomId, nextTimestamp().iso))
        })
        insertedCounts.imRooms = roomRows.length

        const roomMemberRows = dataset.roomMembers.map((member) => {
          const timestamp = nextTimestamp()
          return [member.roomId, member.userId, member.role, timestamp.mysql]
        })
        if (roomMemberRows.length > 0) {
          await runDb.execute(
            formatBulkInsert('im_core.im_room_member', ['room_id', 'user_id', 'role', 'joined_at'], roomMemberRows.length),
            toInsertParams(roomMemberRows)
          )
        }
        roomMemberRows.forEach(([roomId, userId]) => {
          generatedRefs.push(createGeneratedRef('im_room_members', `${roomId}:${userId}`, nextTimestamp().iso))
        })
        insertedCounts.imRoomMembers = roomMemberRows.length

        const roomMessageRows = dataset.roomMessages.map((message) => {
          const timestamp = nextTimestamp()
          return [
            message.roomId,
            message.seq,
            message.messageId,
            message.fromUserId,
            message.content,
            message.clientMsgId,
            timestamp.mysql
          ]
        })
        if (roomMessageRows.length > 0) {
          await runDb.execute(
            formatBulkInsert(
              'im_core.im_room_message',
              ['room_id', 'seq', 'message_id', 'from_user_id', 'content', 'client_msg_id', 'created_at'],
              roomMessageRows.length
            ),
            toInsertParams(roomMessageRows)
          )
        }
        roomMessageRows.forEach(([roomId, seq]) => {
          generatedRefs.push(createGeneratedRef('im_room_messages', `${roomId}:${seq}`, nextTimestamp().iso))
        })
        insertedCounts.imRoomMessages = roomMessageRows.length

        const conversationRows = dataset.conversations.map((conversation) => {
          const timestamp = nextTimestamp()
          return [conversation.conversationId, conversation.userA, conversation.userB, conversation.lastSeq, timestamp.mysql, timestamp.mysql]
        })
        if (conversationRows.length > 0) {
          await runDb.execute(
            formatBulkInsert(
              'im_core.im_conversation',
              ['conversation_id', 'user_a', 'user_b', 'last_seq', 'created_at', 'updated_at'],
              conversationRows.length
            ),
            toInsertParams(conversationRows)
          )
        }
        conversationRows.forEach(([conversationId]) => {
          generatedRefs.push(createGeneratedRef('im_conversations', conversationId, nextTimestamp().iso))
        })
        insertedCounts.imConversations = conversationRows.length

        const privateMessageRows = dataset.privateMessages.map((message) => {
          const timestamp = nextTimestamp()
          return [
            message.conversationId,
            message.seq,
            message.messageId,
            message.fromUserId,
            message.toUserId,
            message.content,
            message.clientMsgId,
            timestamp.mysql
          ]
        })
        if (privateMessageRows.length > 0) {
          await runDb.execute(
            formatBulkInsert(
              'im_core.im_private_message',
              ['conversation_id', 'seq', 'message_id', 'from_user_id', 'to_user_id', 'content', 'client_msg_id', 'created_at'],
              privateMessageRows.length
            ),
            toInsertParams(privateMessageRows)
          )
        }
        privateMessageRows.forEach(([conversationId, seq]) => {
          generatedRefs.push(createGeneratedRef('im_private_messages', `${conversationId}:${seq}`, nextTimestamp().iso))
        })
        insertedCounts.imPrivateMessages = privateMessageRows.length

        await appendEntityRefs({
          entityRefRepository,
          batchId,
          refs: generatedRefs,
          txDb: runDb
        })

        return {
          insertedCounts,
          generatedRefs
        }
      })
    }
  }
}
