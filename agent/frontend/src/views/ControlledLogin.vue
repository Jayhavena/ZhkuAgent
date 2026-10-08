<script setup>
import { ref, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PasswordInput from '../components/PasswordInput.vue'
import { controlledClient as api, errorMessage } from '../api/controlled.js'
import { controlledSession as session } from '../state/controlled-session.js'
import { safeReturnPath } from '../state/controlled-results.js'
import '../styles/controlled.css'
const route = useRoute(), router = useRouter()
const username = ref(''), password = ref(''), busy = ref(false), error = ref('')
let alive = true
onUnmounted(() => { alive = false; password.value = '' })
async function login() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    await api.login(username.value.trim(), password.value)
    if (alive) await router.replace(safeReturnPath(route.query.return))
  } catch (failure) {
    if (alive && failure.code !== 'STALE_RESPONSE') error.value = failure.status === 401 ? '登录未成功，请检查账号和密码。' : errorMessage(failure)
  } finally { password.value = ''; if (alive) busy.value = false }
}
</script>
<template>
  <main class="controlled-app login-page"><RouterLink class="login-brand" to="/"><span class="brand-mark" aria-hidden="true">仲</span>仲园 Agent</RouterLink>
    <div class="login-content"><header class="login-heading"><h1>登录校园工作台</h1><p>校园任务与政策问答</p></header>
      <form @submit.prevent="login"><div class="login-fields"><label for="username">账号</label>
        <input id="username" name="username" v-model="username" autocomplete="username" required maxlength="100" :disabled="busy || session.state.identityBusy" placeholder="请输入账号" />
        <label for="password">密码</label><PasswordInput v-model="password" :disabled="busy || session.state.identityBusy" />
        <p id="password-hint" class="field-hint">请输入启动演示时设置的密码（16～64位英文字符）。</p>
        <p id="login-feedback" class="form-feedback" role="status" aria-live="polite">{{ error || (session.state.reason === 'LOGOUT_UNCONFIRMED' ? '退出尚未完成，请再次点击“重试退出”。' : session.state.reason === 'AUTH_EXPIRED' ? '登录已失效，请重新登录。' : '') }}</p>
        <button class="button primary login-button" type="submit" :disabled="busy || session.state.identityBusy">{{ busy ? '正在登录…' : session.state.identityBusy ? '切换登录状态中…' : '登录' }}</button>
      </div></form>
      <button v-if="session.state.reason === 'LOGOUT_UNCONFIRMED'" class="text-button" type="button" @click="api.logout().catch(() => {})">重试退出</button>
      <p class="login-note">使用演示账号登录，暂不开放注册。</p>
    </div><p class="login-footer">个人项目演示，非学校官方系统</p></main>
</template>
