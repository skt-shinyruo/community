import { describe, expect, it } from 'vitest'
import { POSTS_FILTER, POSTS_ORDER } from '../router/navigation'
import {
  canJumpToLastSeenDivider,
  findLastSeenDividerIndex,
  hasLastSeenDivider,
  isDefaultLatestFeedView,
  resolveAppendPageAfterLoad
} from './postsFeedState'

describe('postsFeedState', () => {
  it('treats only the plain latest homepage as the default latest feed view', () => {
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    expect(
      isDefaultLatestFeedView({
        order: POSTS_ORDER.LATEST,
        filter: POSTS_FILTER.ALL,
        subscribed: false,
        categoryId: '',
        tag: '',
        page: 0
      })
    ).toBe(true)

    expect(
      isDefaultLatestFeedView({
        order: POSTS_ORDER.LATEST,
        filter: POSTS_FILTER.ALL,
        subscribed: true,
        categoryId: '',
        tag: '',
        page: 0
      })
    ).toBe(false)

    expect(
      isDefaultLatestFeedView({
        order: POSTS_ORDER.LATEST,
        filter: POSTS_FILTER.ALL,
        subscribed: false,
        categoryId,
        tag: '',
        page: 0
      })
    ).toBe(false)

    expect(
      isDefaultLatestFeedView({
        order: POSTS_ORDER.LATEST,
        filter: POSTS_FILTER.ALL,
        subscribed: false,
        categoryId: '',
        tag: 'java',
        page: 0
      })
    ).toBe(false)

    expect(
      isDefaultLatestFeedView({
        order: POSTS_ORDER.HOT,
        filter: POSTS_FILTER.ALL,
        subscribed: false,
        categoryId: '',
        tag: '',
        page: 0
      })
    ).toBe(false)
  })

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

  it('restores the current page when append loading fails', () => {
    expect(resolveAppendPageAfterLoad({ previousPage: 2, didLoadSucceed: true })).toBe(3)
    expect(resolveAppendPageAfterLoad({ previousPage: 2, didLoadSucceed: false })).toBe(2)
  })
})
