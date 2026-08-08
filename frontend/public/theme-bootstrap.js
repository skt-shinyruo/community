(() => {
  try {
    const raw = localStorage.getItem('community.ui')
    if (!raw) return
    const value = JSON.parse(raw)
    if (value && (value.theme === 'light' || value.theme === 'dark')) {
      document.documentElement.dataset.theme = value.theme
    }
    if (value && (value.density === 'compact' || value.density === 'comfortable')) {
      document.documentElement.dataset.density = value.density
    }
  } catch {
    // A stale or malformed preference must not prevent application startup.
  }
})()
