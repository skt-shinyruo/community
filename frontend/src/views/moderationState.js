export function moderationActionNeedsDuration(action) {
  return action === 'mute' || action === 'ban'
}

export function resolveModerationDurationSeconds(form = {}) {
  if (!moderationActionNeedsDuration(form.action)) {
    return { valid: true, message: '', durationSeconds: undefined }
  }
  if (form.durationPreset !== 'custom') {
    const preset = Number(form.durationPreset || 0)
    if (Number.isSafeInteger(preset) && preset > 0) {
      return { valid: true, message: '', durationSeconds: preset }
    }
    return { valid: false, message: '请选择处置时长', durationSeconds: undefined }
  }
  const raw = String(form.durationSeconds ?? '').trim()
  if (!raw) {
    return { valid: false, message: '请输入自定义时长（秒）', durationSeconds: undefined }
  }
  if (!/^\d+$/.test(raw)) {
    return { valid: false, message: '自定义时长必须是正整数（秒）', durationSeconds: undefined }
  }
  const value = Number(raw)
  if (value <= 0) {
    return { valid: false, message: '自定义时长必须是正整数（秒）', durationSeconds: undefined }
  }
  if (!Number.isSafeInteger(value)) {
    return { valid: false, message: '自定义时长超出支持的范围', durationSeconds: undefined }
  }
  return { valid: true, message: '', durationSeconds: value }
}
