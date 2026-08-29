import { describe, expect, it } from 'vitest'
import {
  canJumpToLastSeenDivider,
  findLastSeenDividerIndex,
  hasLastSeenDivider
} from './usePostsFeed'

describe('usePostsFeed last-seen divider', () => {
  it('enables last-seen jump only when a divider exists inside the current feed', () => {
    const dividerIndex = findLastSeenDividerIndex(
      [
        { activityAt: 300 },
        { activityAt: 180 },
        { activityAt: 120 }
      ],
      200,
      (item) => item.activityAt
    )

    expect(dividerIndex).toBe(1)
    expect(
      canJumpToLastSeenDivider({
        isLatestFeedView: true,
        newSinceLastSeenCount: 2,
        newHintDismissed: false,
        dividerIndex,
        itemsLength: 3
      })
    ).toBe(true)

    expect(
      canJumpToLastSeenDivider({
        isLatestFeedView: true,
        newSinceLastSeenCount: 3,
        newHintDismissed: false,
        dividerIndex: -1,
        itemsLength: 3
      })
    ).toBe(false)

    expect(
      hasLastSeenDivider({
        isLatestFeedView: true,
        dividerIndex: 0,
        itemsLength: 3
      })
    ).toBe(false)
  })
})
