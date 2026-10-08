<script setup>
defineProps({ fact: { type: Object, required: true }, conditions: { type: Array, default: () => [] }, disabled: Boolean })
defineEmits(['original'])
</script>
<template>
  <article class="rule-fact">
    <h3>{{ fact.text }}</h3>
    <div v-if="Object.keys(fact.when).length" class="fact-conditions"><p>仅在这些条件下确认</p>
      <dl><template v-for="(value, key) in fact.when" :key="key"><dt>{{ conditions.find(c => c.key === key)?.question || key }}</dt><dd>{{ value }}</dd></template></dl>
    </div>
    <div v-for="citation in fact.citations" :key="citation.originalToken" class="fact-citation">
      <!-- 资料和政策文本只作为文字展示，不能把原文中的HTML变成浏览器指令。 -->
      <blockquote>{{ citation.text }}</blockquote>
      <div class="citation-actions"><span>引用版本 {{ citation.revision }}</span><button type="button" :disabled="disabled" @click="$emit('original', citation.originalToken)">核对原文</button></div>
    </div>
  </article>
</template>
<style scoped>
.rule-fact { padding: 24px 0; border-top: 1px solid #d8ddcf; }
h3 { font-size: 19px; line-height: 1.65; font-weight: 600; overflow-wrap: anywhere; }
.fact-conditions { margin-top: 14px; font-size: 14px; color: #465346; }
dl { display: grid; grid-template-columns: minmax(90px, 1fr) 2fr; gap: 6px 16px; margin-top: 8px; }
dt, dd { overflow-wrap: anywhere; }
blockquote { margin: 18px 0 10px; padding-left: 16px; border-left: 3px solid #a9bca5; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; }
.citation-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; font-size: 13px; color: #647261; }
button { border: 1px solid #28553f; background: #fff; padding: 7px 12px; color: #28553f; border-radius: 2px; font: inherit; }
button:focus-visible { outline: 3px solid #28553f; outline-offset: 3px; }
button:disabled { opacity: .5; cursor: wait; }
</style>
