<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { controlledSession as session } from '../state/controlled-session.js'
import { controlledClient as api } from '../api/controlled.js'
import '../styles/controlled.css'
const route = useRoute(), router = useRouter(), open = ref(false), exiting = ref(false)
const stop = watch(() => session.state.actor, actor => {
  if (!actor) router.replace({ path: '/campus/login', query: { return: route.fullPath } })
})
onUnmounted(stop)
async function logout() {
  if (exiting.value) return
  exiting.value = true
  try { await api.logout() } catch { /* 页面已清空；登录页明确显示退出未确认，不能冒充服务端已注销。 */ }
  finally { exiting.value = false; router.replace('/campus/login') }
}
</script>
<template>
  <div class="controlled-app workspace">
    <aside class="sidebar" aria-label="校园导航">
      <div class="brand-row"><RouterLink class="brand" to="/campus/run"><span class="brand-mark" aria-hidden="true">仲</span>仲园 Agent</RouterLink>
        <button class="mobile-menu" type="button" :aria-expanded="open" aria-controls="campus-nav" @click="open = !open">菜单</button></div>
      <div id="campus-nav" class="sidebar-content" :class="{ open }"><p class="nav-caption">校园工作台</p>
        <nav class="app-nav"><RouterLink to="/campus/run" @click="open = false">学分任务</RouterLink>
          <RouterLink to="/campus/history" @click="open = false">我的历史</RouterLink>
          <RouterLink to="/campus/conversation" @click="open = false">重修咨询</RouterLink>
        </nav></div>
      <div class="account"><span class="account-avatar" aria-hidden="true">{{ session.state.actor?.role === 'USER' ? '用' : '维' }}</span>
        <div><strong>{{ session.state.actor?.role === 'USER' ? '当前用户' : '维护者' }}</strong><small>已登录</small></div>
        <button type="button" :disabled="exiting" @click="logout">退出</button></div>
    </aside>
    <main class="main-area"><header class="workspace-top"><span>校园工作台 / {{ route.meta.campusTitle }}</span><span>合成资料演示 · 非学校官方</span></header>
      <div class="work-content"><p v-if="session.state.actor?.role !== 'USER'" class="notice">当前是维护者账号。请退出后使用用户账号进行咨询。</p>
        <RouterView :key="route.path + ':' + session.state.epoch" /></div></main>
  </div>
</template>
