import { describe, expect, it } from 'vitest'

import UserProfileView from './UserProfileView.vue'

describe('UserProfileView route contract', () => {
  it('declares userId as an explicit prop for route-prop pages', () => {
    expect(UserProfileView.props).toBeTruthy()
    expect(UserProfileView.props.userId).toBeTruthy()
  })
})
