import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import UserProfileView from './UserProfileView.vue'

describe('UserProfileView route contract', () => {
  it('declares userId as an explicit prop for route-prop pages', () => {
    expect(UserProfileView.props).toBeTruthy()
    expect(UserProfileView.props.userId).toBeTruthy()
  })

  it('includes independent user level and recent sign-in UI bindings', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/views/UserProfileView.vue'), 'utf-8')

    expect(source).toContain('用户等级 LV {{ Number(profile?.userLevel || 1) }}')
    expect(source).toContain('最近签到 {{ Number(profile?.signInDaysInWindow || 0) }} 天')
    expect(source).toContain('签到用户等级')
  })
})
