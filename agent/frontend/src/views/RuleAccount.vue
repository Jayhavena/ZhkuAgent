<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ruleTaskClient as api } from '../api/rule-task.js'
import { ruleCheckSession as session } from '../state/rule-check-session.js'
import { safeRuleReturn } from '../router/rule-auth.js'
import '../styles/rule-check.css'

const route = useRoute(), router = useRouter()
const mode = computed(() => route.meta.accountMode)
const username = ref(''), password = ref(''), confirmPassword = ref(''), currentPassword = ref('')
const remember = ref(true), busy = ref(false), message = ref(''), loginUnconfirmed = ref(false)
const account = ref(null)
let alive = true
function clearSecrets() { password.value = ''; confirmPassword.value = ''; currentPassword.value = '' }
async function loadAccount() {
  account.value = null
  if (mode.value !== 'account' || !session.state.actor) return
  try { const value = await api.account(); if (alive && mode.value === 'account') account.value = value }
  catch (error) { if (alive && error.code !== 'STALE_RESPONSE') message.value = '无法读取账号信息，请稍后重试。' }
}
watch(mode, () => { clearSecrets(); message.value = ''; loadAccount() })
// 跨窗口身份换代后立即移除账号详情；不能等到用户下次导航才清理。
watch(() => session.state.epoch, () => { account.value = null; clearSecrets() }, { flush: 'sync' })
watch(() => [session.state.actor, session.state.identityBusy], ([actor, transitioning]) => {
  if (alive && mode.value === 'account' && !actor && !transitioning && !busy.value) router.replace('/login')
  // 守卫恢复身份与组件挂载存在竞态：挂载时 actor 可能尚未就绪，loadAccount 会提前返回。
  // 身份后到时必须补拉一次，否则页面会一直显示“未提供”，让用户误以为账号信息丢失。
  else if (alive && mode.value === 'account' && actor && !account.value && !busy.value) loadAccount()
})
onMounted(loadAccount)
onUnmounted(() => { alive = false; clearSecrets(); account.value = null })
async function submit() {
  if (busy.value || session.state.identityBusy || mode.value === 'login' && loginUnconfirmed.value) return
  if (mode.value !== 'login' && password.value !== confirmPassword.value) { message.value = '两次输入的密码不一致。'; return }
  busy.value = true; message.value = ''
  try {
    if (mode.value === 'login') {
      await api.login(username.value.trim(), password.value, remember.value)
      if (alive) await router.replace(safeRuleReturn(route.query.return))
    } else if (mode.value === 'register') {
      await api.register(username.value.trim(), password.value, confirmPassword.value)
      if (alive) { clearSecrets(); await router.push({ path: '/login', query: { return: safeRuleReturn(route.query.return), registered: '1' } }) }
    } else {
      await api.changePassword(currentPassword.value, password.value, confirmPassword.value)
      if (alive) await router.replace({ path: '/login', query: { return: '/rules', changed: '1' } })
    }
  } catch (error) {
    if (alive && mode.value === 'account' && !session.state.actor) {
      await router.replace({ path: '/login', query: { return: '/rules', ...(error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE' ? { changeUnknown: '1' } : {}) } })
    } else if (alive && error.code !== 'STALE_RESPONSE') {
      if (mode.value === 'login' && (error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE')) loginUnconfirmed.value = true
      message.value = error.code === 'NETWORK_UNAVAILABLE' || error.code === 'BAD_RESPONSE'
        ? '操作结果尚未确认，请先核对账号状态，勿重复提交。'
        : error.status === 401 ? '账号或密码不正确，或登录已失效。' : error.status === 409 ? '账号已存在或当前状态冲突。' : '操作未成功，请检查输入或稍后重试。'
    }
  } finally { clearSecrets(); if (alive) busy.value = false }
}
async function checkLogin() {
  if (busy.value) return
  busy.value = true
  try { await api.me(); loginUnconfirmed.value = false; if (alive) await router.replace(safeRuleReturn(route.query.return)) }
  catch (error) {
    if (alive && error.status === 401) { loginUnconfirmed.value = false; message.value = '当前未登录，请重试。' }
    else if (alive) message.value = '暂时无法核对登录状态，请稍后重试。'
  } finally { if (alive) busy.value = false }
}
async function logout() {
  if (busy.value) return
  busy.value = true; message.value = ''
  try { await api.logout(); if (alive) await router.replace('/login') }
  catch { if (alive) message.value = '退出尚未确认，请重试退出。' }
  finally { if (alive) busy.value = false }
}
</script>
<template>
  <main class="account-page">
    <div class="account-card">
      <RouterLink to="/rules">← 返回规则查证</RouterLink>
      <h1>{{ mode === 'login' ? '登录规则查证' : mode === 'register' ? '注册账号' : '账号信息' }}</h1>
      <p v-if="route.query.registered">注册成功，请登录。</p>
      <p v-if="route.query.changed">密码已修改，请重新登录。</p>
      <p v-if="route.query.changeUnknown">修改结果尚未确认，请尝试登录以核对，不要重复提交。</p>
      <template v-if="mode === 'account'">
        <dl><dt>账号</dt><dd>{{ account?.loginName || '未提供' }}</dd><dt>用户 ID</dt><dd>{{ session.state.actor?.userId || '未提供' }}</dd></dl>
        <h2>修改密码</h2>
      </template>
      <form @submit.prevent="submit">
        <template v-if="mode !== 'account'"><label for="account-username">账号</label><input id="account-username" v-model="username" autocomplete="username" required maxlength="100" :disabled="busy" /></template>
        <template v-if="mode === 'account'"><label for="current-password">当前密码</label><input id="current-password" v-model="currentPassword" type="password" autocomplete="current-password" required :disabled="busy" /></template>
        <label for="account-password">{{ mode === 'login' ? '密码' : '新密码' }}</label>
        <input id="account-password" v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required :disabled="busy" />
        <template v-if="mode !== 'login'"><label for="confirm-password">确认密码</label><input id="confirm-password" v-model="confirmPassword" type="password" autocomplete="new-password" required :disabled="busy" /></template>
        <label v-if="mode === 'login'" class="remember"><input v-model="remember" type="checkbox" :disabled="busy" /> 保持登录</label>
        <p class="feedback" role="status" aria-live="polite">{{ message }}</p>
        <button type="submit" :disabled="busy || session.state.identityBusy || (mode === 'login' && loginUnconfirmed)">{{ busy ? '正在处理…' : mode === 'login' ? '登录' : mode === 'register' ? '注册' : '修改密码' }}</button>
      </form>
      <button v-if="mode === 'login' && loginUnconfirmed" type="button" :disabled="busy" @click="checkLogin">核对登录状态</button>
      <p v-if="mode === 'login'"><RouterLink :to="{path:'/register',query:{return:safeRuleReturn(route.query.return)}}">注册新账号</RouterLink></p>
      <p v-if="mode === 'register'"><RouterLink :to="{path:'/login',query:{return:safeRuleReturn(route.query.return)}}">已有账号？登录</RouterLink></p>
      <button v-if="mode === 'account' || session.state.reason === 'LOGOUT_UNCONFIRMED'" type="button" :disabled="busy" @click="logout">{{ session.state.reason === 'LOGOUT_UNCONFIRMED' ? '重试退出' : '退出登录' }}</button>
    </div>
  </main>
</template>
<style scoped>
.account-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:#f5f7fa;color:#172b3a;box-sizing:border-box}.account-card{width:min(100%,440px);background:white;border:1px solid #d9e1e8;border-radius:16px;padding:28px;box-sizing:border-box}.account-card h1{font-size:1.6rem}.account-card h2{font-size:1.15rem}form{display:grid;gap:10px}input:not([type=checkbox]){box-sizing:border-box;width:100%;min-height:44px;padding:10px;border:1px solid #9baab6;border-radius:6px;font-size:16px}.remember{display:flex;align-items:center;gap:8px;min-height:44px}.feedback{min-height:1.5em;color:#a52828}button{min-height:44px;padding:10px 18px;border:0;border-radius:6px;background:#205b78;color:white;cursor:pointer}button:disabled{opacity:.6;cursor:wait}a{color:#205b78}dt{font-weight:bold}dd{margin:0 0 10px;overflow-wrap:anywhere}
</style>
