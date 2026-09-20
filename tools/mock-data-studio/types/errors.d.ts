// Error augmentation for this package's CLI error convention: plain Error
// objects carrying extra fields set at throw sites. Declared once so
// checkJs accepts error.code / error.status / ... without weakening errors.
declare global {
  interface Error {
    code?: string
    status?: number
    batchId?: string
    entityType?: string
  }
}

export {}
