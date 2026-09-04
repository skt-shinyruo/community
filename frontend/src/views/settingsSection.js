// Settings section/query 合同：/settings?section=profile|appearance|addresses。
// section query 是唯一事实来源；缺省或无效值回落到默认 section，并由视图把 URL 规范化。

export const SETTINGS_SECTIONS = Object.freeze([
  { key: 'profile', label: '公开资料' },
  { key: 'appearance', label: '外观' },
  { key: 'addresses', label: '收货地址' }
])

export const DEFAULT_SETTINGS_SECTION = 'profile'

const SECTION_KEYS = new Set(SETTINGS_SECTIONS.map((section) => section.key))

export function isSettingsSection(value) {
  return typeof value === 'string' && SECTION_KEYS.has(value)
}

export function normalizeSettingsSection(value) {
  return isSettingsSection(value) ? value : DEFAULT_SETTINGS_SECTION
}
