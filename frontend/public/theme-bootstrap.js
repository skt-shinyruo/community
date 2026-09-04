(() => {
  try {
    const raw = localStorage.getItem('community.ui')
    const value = raw ? JSON.parse(raw) : null
    let theme = value && (value.theme === 'light' || value.theme === 'dark' || value.theme === 'system')
      ? value.theme
      : 'system'
    if (theme === 'system') {
      theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    }
    document.documentElement.dataset.theme = theme
    const density = value && (value.density === 'compact' || value.density === 'comfortable')
      ? value.density
      : 'compact'
    document.documentElement.dataset.density = density
  } catch {
    // A stale or malformed preference must not prevent application startup.
  }
})()
