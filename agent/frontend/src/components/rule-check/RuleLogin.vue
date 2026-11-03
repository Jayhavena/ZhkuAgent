<script setup>
import { computed } from 'vue'
import PasswordInput from '../PasswordInput.vue'
const props = defineProps(['username', 'password', 'authChecking', 'loginBusy', 'identityBusy', 'reason', 'loginError', 'identityMessage'])
const emit = defineEmits(['update:username', 'update:password', 'login', 'logout', 'checkLogin'])
const usernameModel = computed({ get: () => props.username, set: value => emit('update:username', value) })
</script>

<template>
  <section class="login-view" aria-labelledby="login-title" data-testid="login-view">
    <div class="login-card">
      <div class="welcome-mark welcome-mark--brand" aria-hidden="true">Z</div>
      <h1 id="login-title">登录规则查证</h1>
      <p class="login-description">依据当前可核验的校园规则资料回答；无法确认时会明确说明。</p>
      <p v-if="authChecking" class="inline-status" role="status">正在核对登录状态…</p>
      <form class="login-form" @submit.prevent="$emit('login')">
        <label for="username">账号</label>
        <input id="username" v-model="usernameModel" name="username" autocomplete="username" required maxlength="32" data-testid="username" :disabled="authChecking || loginBusy || identityBusy" />
        <label for="password">密码</label>
        <PasswordInput :model-value="password" @update:model-value="$emit('update:password', $event)" :disabled="authChecking || loginBusy || identityBusy" />
        <p id="password-hint" class="field-hint">使用受控演示账号登录。</p>
        <p id="login-feedback" class="login-feedback" role="status" aria-live="polite">{{ loginError || identityMessage }}</p>
        <button class="primary-button login-submit" type="submit" data-testid="login-submit" :disabled="authChecking || loginBusy || identityBusy || reason === 'LOGIN_UNCONFIRMED'">
          {{ loginBusy || identityBusy ? '正在核对登录状态…' : '登录' }}
        </button>
      </form>
      <button v-if="reason === 'LOGOUT_UNCONFIRMED'" class="text-button recovery-button" type="button" :disabled="identityBusy" @click="$emit('logout')">重试退出</button>
      <button v-if="reason === 'LOGIN_UNCONFIRMED'" class="text-button recovery-button" type="button" :disabled="authChecking || identityBusy" @click="$emit('checkLogin')">核对登录状态</button>
    </div>
  </section>
</template>
