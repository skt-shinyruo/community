import { describe, expect, it } from 'vitest'

import {
  DEFAULT_SETTINGS_SECTION,
  SETTINGS_SECTIONS,
  isSettingsSection,
  normalizeSettingsSection
} from './settingsSection'

describe('views/settingsSection', () => {
  it('exposes the profile / appearance / addresses deep-link sections in order', () => {
    expect(SETTINGS_SECTIONS.map((section) => section.key)).toEqual(['profile', 'appearance', 'addresses'])
    expect(SETTINGS_SECTIONS.every((section) => typeof section.label === 'string' && section.label.length > 0)).toBe(true)
    expect(DEFAULT_SETTINGS_SECTION).toBe('profile')
  })

  it('keeps valid section query values as-is', () => {
    expect(normalizeSettingsSection('profile')).toBe('profile')
    expect(normalizeSettingsSection('appearance')).toBe('appearance')
    expect(normalizeSettingsSection('addresses')).toBe('addresses')
  })

  it('falls back to the default section for missing or invalid query values', () => {
    expect(normalizeSettingsSection(undefined)).toBe('profile')
    expect(normalizeSettingsSection('')).toBe('profile')
    expect(normalizeSettingsSection('bogus')).toBe('profile')
    expect(normalizeSettingsSection('Appearance')).toBe('profile')
    expect(normalizeSettingsSection(['appearance'])).toBe('profile')
    expect(normalizeSettingsSection(['appearance', 'addresses'])).toBe('profile')
    expect(normalizeSettingsSection(null)).toBe('profile')
  })

  it('matches isSettingsSection only for known keys', () => {
    expect(isSettingsSection('appearance')).toBe(true)
    expect(isSettingsSection('security')).toBe(false)
    expect(isSettingsSection(42)).toBe(false)
  })
})
