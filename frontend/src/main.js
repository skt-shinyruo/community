import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './styles.css'
import { useUiStore } from './stores/ui'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

// 初始化 UI 偏好（theme/density/sidebar），尽早应用到 <html> 上。
useUiStore(pinia).init()

app.mount('#app')
