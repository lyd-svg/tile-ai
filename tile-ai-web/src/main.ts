import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'

// 设计令牌(全站唯一样式入口)。当前只定义变量、无人引用,故不影响现有外观。
import './styles/index.css'

createApp(App).use(createPinia()).use(router).mount('#app')
