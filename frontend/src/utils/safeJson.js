// 安全 JSON 解析：避免页面因不可信内容崩溃。

export function safeJsonParse(text, fallback = null) {
  if (!text) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

