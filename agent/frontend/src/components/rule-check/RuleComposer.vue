<script setup>
import { computed } from 'vue'
const props = defineProps(['question', 'hero', 'disabled', 'busy', 'waitingForClarification', 'terminal'])
const emit = defineEmits(['update:question', 'submit'])
const questionModel = computed({ get: () => props.question, set: value => emit('update:question', value) })
</script>

<template>
  <form v-if="hero" class="composer composer--hero" @submit.prevent="$emit('submit')">
    <label class="visually-hidden" for="question-home">输入校园规则问题</label>
    <textarea id="question-home" v-model="questionModel" rows="2" maxlength="2048" required :disabled="disabled" placeholder="输入你的校园规则问题……" data-testid="question-home" @keydown.enter.exact.prevent="$emit('submit')"></textarea>
    <!-- 提示文字放在输入框内部下方，和 V3 一致：让人一眼知道可以直接用自然语言提问。
         首页底部只保留发送按钮，不再重复「校园规则查证」这句，避免与提示行挤在一起。 -->
    <p class="input-hint">自然语言提问即可</p>
    <div class="composer-footer composer-footer--end">
      <button class="send-button" type="submit" aria-label="提交问题" data-testid="submit-question" :disabled="!question.trim() || disabled">{{ busy ? '…' : '↑' }}</button>
    </div>
  </form>
  <!-- 对话态输入区自带底栏容器：分隔线、底部固定与提示文字都归这里，页面只负责把容器放到滚动区之外。 -->
  <div v-else class="composer-bottom">
    <form class="composer composer--bottom" @submit.prevent="$emit('submit')">
      <label class="visually-hidden" for="question-next">提出新的校园规则问题</label>
      <textarea id="question-next" v-model="questionModel" rows="1" maxlength="2048" :disabled="disabled" :placeholder="waitingForClarification ? '请先回答上方条件，或改问其他问题…' : '提出新的校园规则问题…'" data-testid="question-next" @keydown.enter.exact.prevent="$emit('submit')"></textarea>
      <div class="composer-footer">
        <button class="send-button" type="submit" aria-label="提交新问题" data-testid="submit-next" :disabled="!question.trim() || disabled">↑</button>
      </div>
    </form>
    <p v-if="waitingForClarification" class="composer-note">请先完成上方澄清，或改问其他问题。</p>
    <p v-else-if="terminal" class="composer-note">新问题将独立查证，不会自动沿用上一题条件。</p>
    <p v-else class="composer-note">依据不足时会明确说明</p>
  </div>
</template>
