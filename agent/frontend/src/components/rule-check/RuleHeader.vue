<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

// 顶部栏只做展示与事件转发：身份数据仍来自会话状态，退出仍走页面已有的 logout 流程。
const props = defineProps(['actor', 'identityBusy', 'canAskNew', 'newQuestionDisabled'])
const emit = defineEmits(['logout', 'new-question'])

const open = ref(false)
// 身份面板是页内浮层而非路由，因此自行处理外部点击与 Escape 关闭，避免遮挡后无法收起。
const menu = ref(null)
const roleLabel = computed(() => props.actor?.role === 'USER' ? '学生查证身份' : '维护者身份')
const avatarText = computed(() => props.actor?.role === 'USER' ? '学' : '维')

function close() { open.value = false }
function onPointerDown(event) {
  if (open.value && menu.value && !menu.value.contains(event.target)) close()
}
function onKeydown(event) {
  if (event.key === 'Escape' && open.value) {
    close()
    menu.value?.querySelector('button')?.focus()
  }
}
function logout() {
  close()
  emit('logout')
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <header class="app-header" data-testid="app-header">
    <RouterLink to="/rules" class="brand" aria-label="ZhkuAgent 校园规则查证助手首页">
      <span class="brand-mark" aria-hidden="true">Z</span>
      <span class="brand-copy"><strong>ZhkuAgent</strong><small>校园规则查证助手</small></span>
    </RouterLink>
    <div class="header-actions">
      <button v-if="canAskNew" class="text-button new-question-button" type="button" data-testid="new-question" :disabled="newQuestionDisabled" @click="$emit('new-question')">新提问</button>
      <div v-if="actor" ref="menu" class="identity-menu">
        <button class="identity-button" type="button" data-testid="identity-button" :disabled="identityBusy" :aria-expanded="open ? 'true' : 'false'" aria-haspopup="dialog" :aria-label="'账号信息：' + roleLabel" @click="open = !open">
          <span aria-hidden="true">{{ avatarText }}</span>
        </button>
        <div v-if="open" class="identity-popover" role="dialog" aria-label="当前身份">
          <p class="identity-eyebrow">当前身份</p>
          <p class="identity-role">{{ roleLabel }}</p>
          <p class="identity-id">用户 ID：{{ actor.userId }}</p>
          <button class="secondary-button identity-logout" type="button" data-testid="logout" :disabled="identityBusy" @click="logout">退出登录</button>
        </div>
      </div>
    </div>
  </header>
</template>
