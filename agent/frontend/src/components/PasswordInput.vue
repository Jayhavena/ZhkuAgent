<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ modelValue: { type: String, default: '' }, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
const visible = ref(false)
watch(() => props.modelValue, value => { if (!value) visible.value = false })
</script>
<template>
  <div class="password-field">
    <input id="password" name="password" :value="modelValue" @input="emit('update:modelValue', $event.target.value)"
      :type="visible ? 'text' : 'password'" autocomplete="current-password" minlength="16" maxlength="64"
      required :disabled="disabled" aria-describedby="password-hint login-feedback" />
    <button type="button" :disabled="disabled" :aria-label="visible ? '隐藏密码' : '显示密码'"
      :aria-pressed="visible" aria-controls="password" @click="visible = !visible">
      <svg viewBox="0 0 24 24" class="icon" aria-hidden="true"><path d="M2 12s4-6 10-6 10 6 10 6-4 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/><path v-if="visible" d="m3 3 18 18"/></svg>
    </button>
  </div>
</template>
