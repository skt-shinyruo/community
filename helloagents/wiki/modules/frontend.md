# frontend（Vue3 SPA）

## 1. 技术栈
- Vite + Vue3 + Vue Router + Pinia + Axios

## 2. 关键能力（迭代 0）
- 登录页：调用 `/api/auth/login`
- Axios 拦截器：
  - 自动注入 `Authorization: Bearer <accessToken>`
  - 401 自动触发 `/api/auth/refresh` 并重试
- 路由守卫：未登录跳转 `/login`

## 3. 关键文件
- 入口：`frontend/src/main.js`
- 路由：`frontend/src/router/index.js`
- store：`frontend/src/stores/auth.js`
- http client：`frontend/src/api/http.js`
- Vite proxy：`frontend/vite.config.js`（默认转发 `/api` 到 `http://localhost:8080`）

## 4. 本地运行（示例）
- 安装依赖：`npm -C frontend install`
- 启动开发服务器：`npm -C frontend run dev`

