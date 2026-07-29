import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setSessionExpiredHandler } from './api/client'
import { clear } from './stores/session'
import './style.css'

/**
 * 세션이 끊겼을 때(갱신까지 실패) 화면 상태를 비우고 로그인으로 보낸다.
 *
 * client.js 가 라우터를 직접 import 하면 순환 의존이 되므로 여기서 연결한다.
 */
setSessionExpiredHandler(() => {
  clear()
  if (router.currentRoute.value.name !== 'login') {
    router.push({
      name: 'login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
})

createApp(App).use(router).mount('#app')
