#!/usr/bin/env node
/**
 * 自检用的“假账号应用”：只服务于 persistent-login-browser.mjs 的自检验证，不进入产品代码。
 *
 * 为什么需要它：脚本的六个阶段是一个跨进程状态机（注册 -> 登录 -> 关窗重开 -> 仍登录 -> 退出 -> 仍退出），
 * 在 Lead 起好后端之前，只有用这个 mock 把六个阶段整条跑一遍，才能证明脚本自身没有逻辑错误，
 * 避免真正联调时分不清"是脚本写错"还是"后端持久登录坏了"。
 *
 * 它刻意复刻真实实现的对外契约：
 *   - 路由 /rules、/login、/register、/account，/rules 与 /account 需要登录（守卫查 /auth/me）
 *   - 选择器与 agent/frontend/src/views/RuleAccount.vue 一致（#account-username / #account-password /
 *     #confirm-password / label.remember / p.feedback / dl 的"账号"与"用户 ID"）
 *   - 登录用 form-urlencoded 且带 remember-me=true 时才下发长期 Cookie（HttpOnly，30 天）
 *   - 退出后长期凭据作废，会话 Cookie 清除
 * 区别只在于：数据存在内存里、校验规则更宽松、不连数据库。所以它证明的是**脚本逻辑**，不是真实账号系统。
 *
 * 用法：node agent/frontend/tests/manual/mock-account-app.mjs [--port 0] [--out <file 写入实际端口>]
 */
import { createServer } from 'node:http'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = path.resolve(SCRIPT_DIR, '../../../..')
const PREFIX = '/api/zhku/controlled-preview/v2'

const users = new Map() // loginName -> { password, userId, username, accountStatus, createdAt }
const sessions = new Map() // sessionId -> loginName
const rememberTokens = new Map() // token -> loginName

function parseCookies(header) {
  const out = {}
  for (const part of String(header || '').split(';')) {
    const index = part.indexOf('=')
    if (index < 1) continue
    out[part.slice(0, index).trim()] = decodeURIComponent(part.slice(index + 1).trim())
  }
  return out
}

function currentUser(request) {
  const cookies = parseCookies(request.headers.cookie)
  const bySession = cookies.MOCK_SESSION ? sessions.get(cookies.MOCK_SESSION) : null
  if (bySession) return users.get(bySession) || null
  // 与真实实现一致：只有 GET 请求才会用长期 Cookie 恢复身份。
  if (request.method === 'GET') {
    const byRemember = cookies.MOCK_REMEMBER ? rememberTokens.get(cookies.MOCK_REMEMBER) : null
    if (byRemember) return users.get(byRemember) || null
  }
  return null
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = []
    request.on('data', chunk => chunks.push(chunk))
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    request.on('error', reject)
  })
}

function json(response, status, payload, headers = {}) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers })
  response.end(JSON.stringify(payload))
}

const APP_HTML = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>mock 账号应用（自检夹具）</title></head>
<body><div id="app"></div>
<script src="/vendor/vue.global.prod.js"></script>
<script src="/vendor/vue-router.global.prod.js"></script>
<script src="/app.js"></script>
</body></html>`

const APP_JS = `const { createApp, ref, computed, watch, onMounted } = Vue;
const { createRouter, createWebHistory, RouterLink } = VueRouter;
const PREFIX = ${JSON.stringify(PREFIX)};

async function apiJson(pathname, init) {
  const response = await fetch(PREFIX + pathname, Object.assign({ credentials: 'same-origin', cache: 'no-store' }, init));
  let body = null;
  try { body = await response.json() } catch { body = null }
  return { status: response.status, ok: response.ok, body };
}

const Account = {
  props: { mode: String },
  template: \`
  <main>
    <h1>{{ mode === 'login' ? '登录规则查证' : mode === 'register' ? '注册账号' : '账号信息' }}</h1>
    <p v-if="route.query.registered">注册成功，请登录。</p>
    <form v-if="mode !== 'account'" @submit.prevent="submit">
      <label for="account-username">账号</label>
      <input id="account-username" v-model="username" autocomplete="username" />
      <label for="account-password">{{ mode === 'login' ? '密码' : '新密码' }}</label>
      <input id="account-password" v-model="password" type="password" />
      <template v-if="mode === 'register'">
        <label for="confirm-password">确认密码</label>
        <input id="confirm-password" v-model="confirmPassword" type="password" />
      </template>
      <label v-if="mode === 'login'" class="remember"><input v-model="remember" type="checkbox" /> 保持登录</label>
      <p class="feedback" role="status" aria-live="polite">{{ message }}</p>
      <button type="submit" :disabled="busy">{{ busy ? '正在处理…' : mode === 'login' ? '登录' : '注册' }}</button>
    </form>
    <dl v-else>
      <dt>账号</dt><dd>{{ account?.loginName || '未提供' }}</dd>
      <dt>用户 ID</dt><dd>{{ actor?.userId || '未提供' }}</dd>
    </dl>
    <p class="feedback" role="status" aria-live="polite">{{ message }}</p>
    <button v-if="mode === 'account'" type="button" @click="logout">退出登录</button>
  </main>\`,
  setup(props) {
    const { useRoute, useRouter } = VueRouter;
    const route = useRoute(); const router = useRouter();
    const username = ref(''); const password = ref(''); const confirmPassword = ref('');
    const remember = ref(__REMEMBER_DEFAULT__); const busy = ref(false); const message = ref('');
    const account = ref(null); const actor = ref(globalThis.__actor || null);

    async function loadMe() {
      const me = await apiJson('/auth/me');
      actor.value = me.status === 200 ? me.body : null;
      globalThis.__actor = actor.value;
      return actor.value;
    }

    async function submit() {
      busy.value = true; message.value = '';
      try {
        if (props.mode === 'register') {
          if (password.value !== confirmPassword.value) { message.value = '两次输入的密码不一致。'; return }
          const result = await apiJson('/auth/register', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: username.value, password: password.value, confirmPassword: confirmPassword.value })
          });
          if (result.status === 201) { await router.push({ path: '/login', query: { registered: '1' } }); return }
          message.value = result.status === 409 ? '账号已存在或当前状态冲突。' : '注册失败。';
          return;
        }
        const params = new URLSearchParams({ username: username.value, password: password.value, 'remember-me': String(remember.value) });
        const result = await apiJson('/auth/login', {
          method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }, body: params.toString()
        });
        if (result.status === 200) { await loadMe(); await router.replace('/rules'); return }
        message.value = '账号或密码不正确，或登录已失效。';
      } finally { busy.value = false }
    }

    async function logout() {
      busy.value = true;
      try { await apiJson('/auth/logout', { method: 'POST' }); globalThis.__actor = null; actor.value = null; await router.replace('/login') }
      finally { busy.value = false }
    }

    onMounted(async () => {
      if (props.mode === 'account') {
        const me = await loadMe();
        if (!me) { await router.replace('/login'); return }
        const result = await apiJson('/auth/account');
        if (result.status === 200) account.value = result.body;
      }
    });

    return { route, username, password, confirmPassword, remember, busy, message, account, actor, submit, logout };
  }
};

const Rules = {
  template: \`<main><h1>规则查证（mock）</h1>
    <p>已登录：{{ actor ? actor.username : '未登录' }}</p>
    <div v-if="actor"><button data-testid="identity-button" @click="open = !open">账号菜单</button>
      <div v-if="open" role="dialog"><button data-testid="logout" @click="logout">退出登录</button></div></div>
    <p><RouterLink to="/account">账号</RouterLink></p></main>\`,
  setup() {
    const { useRouter } = VueRouter;
    const router = useRouter();
    const actor = ref(globalThis.__actor || null); const open = ref(false);
    onMounted(async () => { const me = await apiJson('/auth/me'); actor.value = me.status === 200 ? me.body : null; globalThis.__actor = actor.value });
    async function logout() { await apiJson('/auth/logout', { method: 'POST' }); globalThis.__actor = null; await router.replace('/login') }
    return { actor, open, logout };
  }
};

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/rules' },
    { path: '/rules', component: Rules, meta: { requiresRuleAuth: true } },
    { path: '/login', component: Account, props: { mode: 'login' } },
    { path: '/register', component: Account, props: { mode: 'register' } },
    { path: '/account', component: Account, props: { mode: 'account' }, meta: { requiresRuleAuth: true } },
    { path: '/:pathMatch(.*)*', redirect: '/rules' }
  ]
});

// 与 agent/frontend/src/router/rule-auth.js 同构：需要登录时先问 /auth/me，401 就跳登录页。
router.beforeEach(async (to) => {
  if (!to.meta.requiresRuleAuth || globalThis.__actor) return true;
  const me = await apiJson('/auth/me');
  if (me.status === 200) { globalThis.__actor = me.body; return true }
  return { path: '/login', query: { return: to.fullPath } };
});

createApp({ template: '<RouterView />' }).use(router).component('RouterLink', RouterLink).mount('#app');
`

async function vendorBundle(name) {
  const candidates = {
    vue: 'agent/frontend/node_modules/vue/dist/vue.global.prod.js',
    'vue-router': 'agent/frontend/node_modules/vue-router/dist/vue-router.global.prod.js'
  }
  return await readFile(path.join(REPO_ROOT, candidates[name]))
}

async function main() {
  const argv = process.argv.slice(2)
  const portArg = argv.indexOf('--port')
  const outArg = argv.indexOf('--out')
  const port = portArg >= 0 ? Number(argv[portArg + 1]) : 0
  // 负向对照：故意忽略 remember-me、不下发长期 Cookie。
  // 用来证明"脚本真的能抓到持久登录失效"，而不是无论后端怎样都通过。
  const breakPersistent = argv.includes('--break-persistent')
  // 让"保持登录"默认不勾选，用来验证脚本的 ensureChecked 真的会去点它。
  // 真实前端 RuleAccount.vue 的 remember 默认是 true，所以这条只在夹具里出现。
  const rememberDefaultFalse = argv.includes('--remember-default-false')
  const vueBundle = await vendorBundle('vue')
  const routerBundle = await vendorBundle('vue-router')

  const server = createServer(async (request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1')
    const pathname = url.pathname

    if (pathname === '/vendor/vue.global.prod.js') {
      response.writeHead(200, { 'Content-Type': 'application/javascript; charset=utf-8' }); response.end(vueBundle); return
    }
    if (pathname === '/vendor/vue-router.global.prod.js') {
      response.writeHead(200, { 'Content-Type': 'application/javascript; charset=utf-8' }); response.end(routerBundle); return
    }
    if (pathname === '/app.js') {
      // 把夹具开关注入到页面脚本里；真实前端不需要这个替换。
      const body = APP_JS.replace('__REMEMBER_DEFAULT__', String(rememberDefaultFalse ? false : true))
      response.writeHead(200, { 'Content-Type': 'application/javascript; charset=utf-8', 'Cache-Control': 'no-store' }); response.end(body); return
    }

    if (pathname.startsWith(PREFIX)) {
      const route = pathname.slice(PREFIX.length)
      const headers = {}

      if (route === '/auth/register' && request.method === 'POST') {
        let payload
        try { payload = JSON.parse(await readBody(request)) } catch { json(response, 400, { code: 'INVALID_INPUT' }); return }
        const { username, password, confirmPassword } = payload || {}
        if (!username || !password) { json(response, 400, { code: 'INVALID_INPUT' }); return }
        if (password !== confirmPassword) { json(response, 400, { code: 'INVALID_INPUT', message: '两次输入的密码不一致' }); return }
        if (users.has(username)) { json(response, 409, { code: 'LOGIN_EXISTS' }); return }
        const account = { loginName: username, username, password, userId: randomUUID(), accountStatus: 'ACTIVE', createdAt: new Date().toISOString() }
        users.set(username, account)
        json(response, 201, { userId: account.userId, username, loginName: username, message: '注册成功，请登录' })
        return
      }

      if (route === '/auth/login' && request.method === 'POST') {
        const params = new URLSearchParams(await readBody(request))
        const name = params.get('username') || ''
        const user = users.get(name)
        if (!user || user.password !== (params.get('password') || '')) { json(response, 401, { code: 'BAD_CREDENTIALS' }); return }
        const sessionId = randomUUID()
        sessions.set(sessionId, name)
        headers['Set-Cookie'] = [`MOCK_SESSION=${sessionId}; Path=/; HttpOnly; SameSite=Strict`]
        // 与 PersistentPreviewLogin 一致：只有 remember-me=true 才发长期 Cookie。
        if (params.get('remember-me') === 'true' && !breakPersistent) {
          const token = randomUUID().replaceAll('-', '')
          rememberTokens.set(token, name)
          headers['Set-Cookie'].push(`MOCK_REMEMBER=${token}; Path=${PREFIX}; Max-Age=2592000; HttpOnly; SameSite=Strict`)
        }
        json(response, 200, { userId: user.userId, username: user.username }, headers)
        return
      }

      if (route === '/auth/logout' && request.method === 'POST') {
        const cookies = parseCookies(request.headers.cookie)
        if (cookies.MOCK_SESSION) sessions.delete(cookies.MOCK_SESSION)
        // 真实实现会撤销当前会话绑定的长期凭据；这里同样作废，否则"退出后仍登录"会被漏掉。
        if (cookies.MOCK_REMEMBER) rememberTokens.delete(cookies.MOCK_REMEMBER)
        headers['Set-Cookie'] = ['MOCK_SESSION=; Path=/; Max-Age=0', `MOCK_REMEMBER=; Path=${PREFIX}; Max-Age=0`]
        json(response, 204, null, headers)
        return
      }

      const user = currentUser(request)
      if (/^\/auth\/(me|account)$/.test(route)) {
        if (!user) { json(response, 401, { code: 'UNAUTHENTICATED' }); return }
        if (route === '/auth/me') {
          json(response, 200, { userId: user.userId, username: user.username, accountStatus: user.accountStatus, createdAt: user.createdAt, role: 'USER' })
          return
        }
        json(response, 200, { userId: user.userId, loginName: user.loginName, username: user.username, accountStatus: user.accountStatus, createdAt: user.createdAt })
        return
      }

      json(response, 404, { code: 'NOT_FOUND' })
      return
    }

    // SPA 历史回退：所有页面路由都返回同一份 HTML，由 vue-router 决定渲染什么。
    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
    response.end(APP_HTML)
  })

  await new Promise(resolve => server.listen(port, '127.0.0.1', resolve))
  const actual = server.address().port
  const url = `http://127.0.0.1:${actual}`
  if (outArg >= 0) await writeFile(argv[outArg + 1], JSON.stringify({ url, port: actual }))
  process.stdout.write(`mock 账号应用已启动：${url}\n`)
}

await main()
