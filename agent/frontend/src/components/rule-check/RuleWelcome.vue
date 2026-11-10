<script setup>
import RuleComposer from './RuleComposer.vue'
defineProps(['question', 'composerDisabled', 'busy', 'sampleQuestions'])
defineEmits(['update:question', 'submit', 'example'])
</script>

<template>
  <section class="welcome-view" aria-labelledby="welcome-title">
    <!-- 欢迎标记只用内联 SVG，不引入图标库；星芒表示“提问后由系统查证”，不是状态指示。 -->
    <div class="welcome-mark" aria-hidden="true">
      <svg viewBox="0 0 24 24" focusable="false">
        <path d="M11.6 2.4a.6.6 0 0 1 1.14 0l.9 4.06a4.3 4.3 0 0 0 3.3 3.3l4.06.9a.6.6 0 0 1 0 1.14l-4.06.9a4.3 4.3 0 0 0-3.3 3.3l-.9 4.06a.6.6 0 0 1-1.14 0l-.9-4.06a4.3 4.3 0 0 0-3.3-3.3l-4.06-.9a.6.6 0 0 1 0-1.14l4.06-.9a4.3 4.3 0 0 0 3.3-3.3Z" />
      </svg>
    </div>
    <h1 id="welcome-title" data-testid="welcome-title">校园规则，直接问。</h1>
    <p class="welcome-description">关于选课、学分和学校规定，试着在这里提问。</p>
    <RuleComposer :hero="true" :question="question" :disabled="composerDisabled" :busy="busy" @update:question="$emit('update:question', $event)" @submit="$emit('submit')" />
    <div class="example-prompts" aria-label="示例问题" data-testid="example-prompts">
      <span class="example-label">试试这样问</span>
      <button v-for="example in sampleQuestions" :key="example" type="button" :disabled="composerDisabled" @click="$emit('example', example)">{{ example }}</button>
    </div>
  </section>
</template>
