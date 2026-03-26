import { afterEach } from 'vitest'

afterEach(() => {
  if (typeof document === 'undefined' || !document.body) return
  document.body.innerHTML = ''
})
