import { getFollowStatuses } from '../api/services/socialService'
import { batchUserSummary } from '../api/services/userService'
import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'

const USER_ENTITY_TYPE = 3

export async function hydrateFollowRelations(relations, { authed = false, viewerUserId = '' } = {}) {
  const list = Array.isArray(relations) ? relations : []
  const targetIds = list
    .map((relation) => normalizeOpaqueId(relation?.targetId))
    .filter(Boolean)
  const viewerId = normalizeOpaqueId(viewerUserId)
  const statusTargetIds = authed
    ? targetIds.filter((targetId) => !sameOpaqueId(targetId, viewerId))
    : []

  const [usersResponse, statusesResponse] = await Promise.all([
    batchUserSummary(targetIds).catch(() => ({ data: [] })),
    statusTargetIds.length > 0
      ? getFollowStatuses(USER_ENTITY_TYPE, statusTargetIds).catch(() => ({ data: {} }))
      : Promise.resolve({ data: {} })
  ])
  const usersById = new Map(
    (Array.isArray(usersResponse?.data) ? usersResponse.data : [])
      .map((user) => [normalizeOpaqueId(user?.id), user])
      .filter(([userId]) => Boolean(userId))
  )
  const statuses = statusesResponse?.data && typeof statusesResponse.data === 'object'
    ? statusesResponse.data
    : {}

  return list.map((relation) => {
    const targetId = normalizeOpaqueId(relation?.targetId)
    return {
      ...relation,
      targetId,
      user: usersById.get(targetId) || null,
      hasFollowed: statuses[targetId] === true
    }
  })
}
