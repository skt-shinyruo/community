import { describe, expect, it } from 'vitest'
import {
  buildDriveBreadcrumb,
  formatDriveBytes,
  normalizeDriveEntry,
  normalizeDriveQuota,
  reduceDriveSelection,
  validateShareForm
} from './driveState'

describe('driveState', () => {
  it('formatDriveBytes should use binary units for quota display', () => {
    expect(formatDriveBytes(0)).toBe('0 B')
    expect(formatDriveBytes(1024)).toBe('1 KB')
    expect(formatDriveBytes(10737418240)).toBe('10 GB')
  })

  it('normalizeDriveQuota should calculate percentage without exceeding 100', () => {
    expect(normalizeDriveQuota({ quotaBytes: 100, usedBytes: 150 })).toEqual({
      quotaBytes: 100,
      usedBytes: 150,
      remainingBytes: 0,
      usedPercent: 100,
      label: '150 B / 100 B'
    })
  })

  it('buildDriveBreadcrumb should include root and ancestors', () => {
    expect(buildDriveBreadcrumb([
      { entryId: 'a', name: 'Docs' },
      { entryId: 'b', name: 'Reports' }
    ])).toEqual([
      { entryId: '', name: '我的文件' },
      { entryId: 'a', name: 'Docs' },
      { entryId: 'b', name: 'Reports' }
    ])
  })

  it('normalizeDriveEntry should expose booleans for UI actions', () => {
    const file = normalizeDriveEntry({ entryId: '1', type: 'FILE', status: 'ACTIVE', sizeBytes: 8, name: 'a.txt', canShare: true })
    const privateFile = normalizeDriveEntry({ entryId: '3', type: 'FILE', status: 'ACTIVE', sizeBytes: 8, name: 'b.txt', canShare: false })
    const trashed = normalizeDriveEntry({ entryId: '2', type: 'FOLDER', status: 'TRASHED', name: 'Old' })

    expect(file.canDownload).toBe(true)
    expect(file.canShare).toBe(true)
    expect(file.statusLabel).toBe('可用')
    expect(file.visibilityLabel).toBe('可分享')
    expect(privateFile.canShare).toBe(false)
    expect(privateFile.visibilityLabel).toBe('私有')
    expect(trashed.canShare).toBe(false)
    expect(trashed.canRestore).toBe(true)
    expect(trashed.statusLabel).toBe('回收站')
    expect(trashed.visibilityLabel).toBe('私有')
  })

  it('validateShareForm should require password and future expiry', () => {
    expect(validateShareForm({ password: '', expiresAt: '2026-05-10T00:00:00Z' }, new Date('2026-05-09T00:00:00Z'))).toEqual({
      valid: false,
      message: '请输入提取码'
    })
    expect(validateShareForm({ password: '1234', expiresAt: '2026-05-08T00:00:00Z' }, new Date('2026-05-09T00:00:00Z'))).toEqual({
      valid: false,
      message: '有效期必须晚于当前时间'
    })
  })

  it('reduceDriveSelection should clear missing selected entry after refresh', () => {
    expect(reduceDriveSelection('2', [{ entryId: '1' }])).toBe('')
    expect(reduceDriveSelection('1', [{ entryId: '1' }])).toBe('1')
  })
})
