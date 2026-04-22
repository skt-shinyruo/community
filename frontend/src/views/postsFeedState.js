import { POSTS_FILTER, POSTS_ORDER } from '../router/navigation'
import { hasOpaqueId } from '../utils/opaqueId'

export function isDefaultLatestFeedView({
  order = POSTS_ORDER.LATEST,
  filter = POSTS_FILTER.ALL,
  subscribed = false,
  categoryId = '',
  tag = '',
  page = 0
} = {}) {
  return (
    order === POSTS_ORDER.LATEST &&
    filter === POSTS_FILTER.ALL &&
    subscribed !== true &&
    !hasOpaqueId(categoryId) &&
    !String(tag || '').trim() &&
    Number(page || 0) === 0
  )
}

export function findLastSeenDividerIndex(items, baselineAt, getActivityAt = (item) => item?.activityAt) {
  const baseline = Number(baselineAt || 0)
  if (!Number.isFinite(baseline) || baseline <= 0) return -1

  const list = Array.isArray(items) ? items : []
  for (let i = 0; i < list.length; i += 1) {
    const activityAt = Number(getActivityAt(list[i]) || 0)
    if (activityAt > 0 && activityAt <= baseline) return i
  }
  return -1
}

export function hasLastSeenDivider({ isLatestFeedView, dividerIndex, itemsLength } = {}) {
  const count = Number(itemsLength || 0)
  return !!isLatestFeedView && Number(dividerIndex) > 0 && Number(dividerIndex) < count
}

export function canJumpToLastSeenDivider({
  isLatestFeedView,
  newSinceLastSeenCount,
  newHintDismissed,
  dividerIndex,
  itemsLength
} = {}) {
  return (
    !!isLatestFeedView &&
    Number(newSinceLastSeenCount || 0) > 0 &&
    newHintDismissed !== true &&
    hasLastSeenDivider({ isLatestFeedView, dividerIndex, itemsLength })
  )
}

export function resolveAppendPageAfterLoad({ previousPage, didLoadSucceed } = {}) {
  const page = Number(previousPage || 0)
  return didLoadSucceed ? page + 1 : page
}
